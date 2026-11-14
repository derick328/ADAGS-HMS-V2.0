package com.adags.modules.inventory.application;

import com.adags.modules.inventory.api.StockReceiptRequest;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.MovementType;
import com.adags.modules.inventory.domain.StockBalanceRepository;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StockBatchStatus;
import com.adags.modules.inventory.domain.StockMovement;
import com.adags.modules.inventory.domain.StockMovementRepository;
import com.adags.modules.inventory.domain.Store;
import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.modules.inventory.domain.Supplier;
import com.adags.modules.inventory.domain.SupplierRepository;
import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.Money;
import com.adags.shared.numbering.GaplessNumberAllocator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class StockReceiptService {
    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");
    private final NamedParameterJdbcTemplate jdbc;
    private final MedicinePackagingService packaging;
    private final ItemRepository items;
    private final StoreRepository stores;
    private final SupplierRepository suppliers;
    private final StockBatchRepository batches;
    private final StockMovementRepository movements;
    private final StockBalanceRepository balances;
    private final GaplessNumberAllocator numbers;
    private final AuditRecorder audit;
    private final DomainEventPublisher events;
    private final ObjectMapper mapper;

    public StockReceiptService(NamedParameterJdbcTemplate jdbc, MedicinePackagingService packaging,
                               ItemRepository items, StoreRepository stores, SupplierRepository suppliers,
                               StockBatchRepository batches, StockMovementRepository movements,
                               StockBalanceRepository balances, GaplessNumberAllocator numbers,
                               AuditRecorder audit, DomainEventPublisher events, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.packaging = packaging;
        this.items = items;
        this.stores = stores;
        this.suppliers = suppliers;
        this.batches = batches;
        this.movements = movements;
        this.balances = balances;
        this.numbers = numbers;
        this.audit = audit;
        this.events = events;
        this.mapper = mapper;
    }

    public record Receipt(UUID id, String receiptNumber, UUID facilityId, UUID storeId, UUID supplierId,
                          String supplierName, UUID receivedBy, String receivedByName,
                          String invoiceNumber, String deliveryNoteNumber,
                          LocalDate invoiceDate, LocalDate deliveryDate, String notes,
                          Long totalReceivedCostMinor,
                          Long totalAcceptedCostMinor, String currencyCode, Instant confirmedAt,
                          List<LineResult> lines) {
    }

    public record LineResult(UUID id, UUID itemId, String itemName, String baseUnit,
                             String purchaseUnit, int factor, Integer blistersPerPack,
                             Integer unitsPerBlister, int receivedQuantity,
                             int acceptedQuantity, int rejectedQuantity, int missingQuantity,
                             int acceptedBaseQuantity, int rejectedBaseQuantity, Long receivedCostMinor,
                             Long acceptedCostMinor, String batchNumber, LocalDate expiryDate,
                             Integer orderedQuantity, Long unitPurchaseCostMinor,
                             String discrepancyReason, String notes) {
    }

    private record Prepared(StockReceiptRequest.Line input, Item item,
                            MedicinePackagingService.Packaging packageDefinition,
                            int missing, int acceptedBase, int rejectedBase,
                            Long receivedCost, Long acceptedCost) {
    }

    @Transactional
    public Receipt confirm(StockReceiptRequest request, UUID actorId) {
        String fingerprint = fingerprint(request);
        Receipt existing = findByIdempotencyKey(request.idempotencyKey());
        if (existing != null) {
            requireSameRequest(existing.id(), fingerprint);
            return existing;
        }
        Store store = stores.findById(request.storeId())
                .orElseThrow(() -> new NoSuchElementException("Receiving store not found"));
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new NoSuchElementException("Supplier not found"));
        if (!store.active() || !store.type().equals("MAIN_PHARMACY") || !supplier.active()) {
            throw new IllegalArgumentException("An active pharmacy store and supplier are required");
        }
        LocalDate today = LocalDate.now(FACILITY_ZONE);
        if (request.invoiceDate().isAfter(today) || request.deliveryDate().isAfter(today)) {
            throw new IllegalArgumentException("Invoice and delivery dates cannot be in the future");
        }
        List<Prepared> prepared = new ArrayList<>();
        Long totalReceived = 0L;
        Long totalAccepted = 0L;
        for (StockReceiptRequest.Line line : request.lines()) {
            if (line.receivedQuantity() < 0 || line.acceptedQuantity() < 0 || line.rejectedQuantity() < 0
                    || line.acceptedQuantity() > line.receivedQuantity()
                    || line.rejectedQuantity() > line.receivedQuantity()
                    || line.unitPurchaseCostMinor() != null && line.unitPurchaseCostMinor() < 0) {
                throw new IllegalArgumentException("Receipt quantities and cost must be nonnegative and accepted/rejected cannot exceed received");
            }
            if (line.receivedQuantity() == 0 && (line.orderedQuantity() == null || line.orderedQuantity() == 0)) {
                throw new IllegalArgumentException("A receipt line needs an ordered or received quantity");
            }
            Item item = items.findById(line.itemId())
                    .orElseThrow(() -> new NoSuchElementException("Medicine not found"));
            item.assertDispensable();
            if (!item.active()) {
                throw new IllegalArgumentException("Medicine is inactive");
            }
            MedicinePackagingService.Packaging selected = packaging.approved(item.id(), line.purchaseUnit());
            if ((line.blistersPerPack() == null) != (line.unitsPerBlister() == null)) {
                throw new IllegalArgumentException("Enter both blisters per pack and units per blister");
            }
            if (line.blistersPerPack() != null) {
                if (line.blistersPerPack() <= 0 || line.unitsPerBlister() <= 0
                        || Math.multiplyExact(line.blistersPerPack(), line.unitsPerBlister()) != selected.factor()
                        || selected.blistersPerPack() != null && !selected.blistersPerPack().equals(line.blistersPerPack())
                        || selected.unitsPerBlister() != null && !selected.unitsPerBlister().equals(line.unitsPerBlister())) {
                    throw new IllegalArgumentException("Blister counts must match the approved pack conversion");
                }
            }
            if (!selected.canSplit() && line.unitPurchaseCostMinor() != null
                    && line.unitPurchaseCostMinor() % selected.factor() != 0) {
                throw new IllegalArgumentException("Cost of a sealed package must divide exactly across its inventory units");
            }
            if (!line.expiryDate().isAfter(today)) {
                throw new IllegalArgumentException("Expiry date must be after the facility-local date");
            }
            if (line.receivedQuantity() != Math.addExact(line.acceptedQuantity(), line.rejectedQuantity())) {
                throw new IllegalArgumentException("Received quantity must equal accepted plus rejected");
            }
            int missing = line.orderedQuantity() == null ? 0
                    : Math.max(0, line.orderedQuantity() - line.receivedQuantity());
            int acceptedBase = Math.multiplyExact(line.acceptedQuantity(), selected.factor());
            int rejectedBase = Math.multiplyExact(line.rejectedQuantity(), selected.factor());
            Long receivedCost = line.unitPurchaseCostMinor() == null ? null
                    : Math.multiplyExact((long) line.receivedQuantity(), line.unitPurchaseCostMinor());
            Long acceptedCost = line.unitPurchaseCostMinor() == null ? null
                    : Math.multiplyExact((long) line.acceptedQuantity(), line.unitPurchaseCostMinor());
            totalReceived = totalReceived == null || receivedCost == null ? null : Math.addExact(totalReceived, receivedCost);
            totalAccepted = totalAccepted == null || acceptedCost == null ? null : Math.addExact(totalAccepted, acceptedCost);
            prepared.add(new Prepared(line, item, selected, missing, acceptedBase, rejectedBase,
                    receivedCost, acceptedCost));
        }

        UUID receiptId = UUID.randomUUID();
        List<UUID> inserted = jdbc.query("""
                INSERT INTO stock_receipt
                  (id, facility_id, store_id, supplier_id, receipt_number, invoice_number,
                   delivery_note_number, invoice_date, delivery_date, notes, idempotency_key,
                   request_fingerprint, received_by, total_received_cost_minor,
                   total_accepted_cost_minor, currency_code)
                VALUES (:id, :facilityId, :storeId, :supplierId, :temporaryNumber, :invoice,
                        :deliveryNote, :invoiceDate, :deliveryDate, :notes, :key,
                        :fingerprint, :actorId, :receivedCost, :acceptedCost, 'TZS')
                ON CONFLICT (idempotency_key) DO NOTHING RETURNING id
                """, new MapSqlParameterSource().addValue("id", receiptId)
                .addValue("facilityId", store.facilityId()).addValue("storeId", store.id())
                .addValue("supplierId", supplier.id()).addValue("temporaryNumber", receiptId.toString())
                .addValue("invoice", request.invoiceNumber().trim())
                .addValue("deliveryNote", request.deliveryNoteNumber().trim())
                .addValue("invoiceDate", request.invoiceDate()).addValue("deliveryDate", request.deliveryDate())
                .addValue("notes", request.notes()).addValue("key", request.idempotencyKey())
                .addValue("fingerprint", fingerprint).addValue("actorId", actorId)
                .addValue("receivedCost", totalReceived).addValue("acceptedCost", totalAccepted),
                (rs, row) -> (UUID) rs.getObject("id"));
        if (inserted.isEmpty()) {
            Receipt raced = findByIdempotencyKey(request.idempotencyKey());
            requireSameRequest(raced.id(), fingerprint);
            return raced;
        }
        String receiptNumber = numbers.allocate("STOCK_RECEIPT", store.facilityId(), today.getYear(), 6);
        jdbc.update("UPDATE stock_receipt SET receipt_number = :number WHERE id = :id",
                new MapSqlParameterSource().addValue("number", receiptNumber).addValue("id", receiptId));

        int index = 0;
        for (Prepared p : prepared) {
            UUID lineId = UUID.randomUUID();
            StockReceiptRequest.Line line = p.input();
            jdbc.update("""
                    INSERT INTO stock_receipt_line
                      (id, receipt_id, line_number, item_id, packaging_id, purchase_unit,
                       quantity_in_base_units, ordered_quantity, received_quantity, accepted_quantity,
                       rejected_quantity, missing_quantity, accepted_base_quantity, rejected_base_quantity,
                       batch_number, expiry_date, unit_purchase_cost_minor, received_cost_minor,
                       accepted_cost_minor, discrepancy_reason, notes,
                       blisters_per_pack, units_per_blister)
                    VALUES (:id, :receiptId, :lineNumber, :itemId, :packagingId, :unit,
                            :factor, :ordered, :received, :accepted, :rejected, :missing,
                            :acceptedBase, :rejectedBase, :batch, :expiry, :unitCost,
                            :receivedCost, :acceptedCost, :reason, :notes,
                            :blistersPerPack, :unitsPerBlister)
                    """, new MapSqlParameterSource().addValue("id", lineId).addValue("receiptId", receiptId)
                    .addValue("lineNumber", ++index).addValue("itemId", p.item().id())
                    .addValue("packagingId", p.packageDefinition().id()).addValue("unit", p.packageDefinition().unit())
                    .addValue("factor", p.packageDefinition().factor()).addValue("ordered", line.orderedQuantity())
                    .addValue("received", line.receivedQuantity()).addValue("accepted", line.acceptedQuantity())
                    .addValue("rejected", line.rejectedQuantity()).addValue("missing", p.missing())
                    .addValue("acceptedBase", p.acceptedBase()).addValue("rejectedBase", p.rejectedBase())
                    .addValue("batch", line.batchNumber().trim()).addValue("expiry", line.expiryDate())
                    .addValue("unitCost", line.unitPurchaseCostMinor()).addValue("receivedCost", p.receivedCost())
                    .addValue("acceptedCost", p.acceptedCost()).addValue("reason", line.discrepancyReason())
                    .addValue("notes", line.notes())
                    .addValue("blistersPerPack", line.blistersPerPack())
                    .addValue("unitsPerBlister", line.unitsPerBlister()));
            int multiple = p.packageDefinition().canSplit() ? 1 : p.packageDefinition().factor();
            receiveTranches(p, lineId, store, supplier, request.deliveryNoteNumber(), actorId,
                    p.acceptedBase(), p.acceptedCost(), StockBatchStatus.ACTIVE, multiple);
            Long rejectedCost = p.receivedCost() == null ? null : p.receivedCost() - p.acceptedCost();
            receiveTranches(p, lineId, store, supplier, request.deliveryNoteNumber(), actorId,
                    p.rejectedBase(), rejectedCost, StockBatchStatus.QUARANTINED, multiple);
        }
        audit.record(actorId, "STOCK_RECEIPT_CONFIRM", "STOCK_RECEIPT", receiptId, null);
        events.publish("StockReceiptConfirmed", java.util.Map.of("receiptId", receiptId.toString(),
                "facilityId", store.facilityId().toString()));
        return findById(receiptId);
    }

    private void receiveTranches(Prepared p, UUID lineId, Store store, Supplier supplier,
                                 String deliveryNote, UUID actorId, int baseQuantity, Long value,
                                 StockBatchStatus status, int multiple) {
        if (baseQuantity == 0) return;
        if (value == null) {
            receiveTranche(p, lineId, store, supplier, deliveryNote, actorId,
                    baseQuantity, 0, status, multiple);
            return;
        }
        long floorCost = value / baseQuantity;
        int higherCostUnits = Math.toIntExact(value % baseQuantity);
        int lowerCostUnits = baseQuantity - higherCostUnits;
        if (lowerCostUnits > 0) receiveTranche(p, lineId, store, supplier, deliveryNote, actorId,
                lowerCostUnits, floorCost, status, multiple);
        if (higherCostUnits > 0) receiveTranche(p, lineId, store, supplier, deliveryNote, actorId,
                higherCostUnits, floorCost + 1, status, multiple);
    }

    private void receiveTranche(Prepared p, UUID lineId, Store store, Supplier supplier,
                                String deliveryNote, UUID actorId, int quantity, long unitCost,
                                StockBatchStatus status, int multiple) {
        StockReceiptRequest.Line line = p.input();
        StockBatch saved = batches.insert(new StockBatch(null, p.item().id(), store.id(),
                line.batchNumber().trim(), line.expiryDate(), new Money(unitCost, "TZS"),
                supplier.id(), deliveryNote.trim(), status, actorId, null, line.notes()));
        jdbc.update("""
                UPDATE stock_batch SET receipt_line_id = :lineId, dispense_multiple_base = :multiple,
                    cost_known = :costKnown
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("lineId", lineId)
                .addValue("multiple", multiple).addValue("costKnown", line.unitPurchaseCostMinor() != null)
                .addValue("id", saved.id()));
        balances.applyDelta(p.item().id(), saved.id(), store.id(), quantity);
        movements.insert(StockMovement.record(p.item().id(), saved.id(), store.id(), quantity,
                MovementType.RECEIPT, "STOCK_RECEIPT_LINE", lineId, actorId));
    }

    public Receipt findById(UUID id) {
        List<Receipt> rows = jdbc.query("""
                SELECT r.id, r.receipt_number, r.facility_id, r.store_id, r.supplier_id,
                       s.name AS supplier_name, r.received_by, u.username AS received_by_name,
                       r.invoice_number, r.delivery_note_number,
                       r.invoice_date, r.delivery_date, r.notes, r.total_received_cost_minor,
                       r.total_accepted_cost_minor, r.currency_code, r.confirmed_at
                FROM stock_receipt r JOIN supplier s ON s.id = r.supplier_id
                JOIN app_user u ON u.id = r.received_by WHERE r.id = :id
                """, new MapSqlParameterSource("id", id), (rs, row) -> new Receipt(
                (UUID) rs.getObject("id"), rs.getString("receipt_number"),
                (UUID) rs.getObject("facility_id"), (UUID) rs.getObject("store_id"),
                (UUID) rs.getObject("supplier_id"), rs.getString("supplier_name"),
                (UUID) rs.getObject("received_by"), rs.getString("received_by_name"),
                rs.getString("invoice_number"),
                rs.getString("delivery_note_number"), rs.getDate("invoice_date").toLocalDate(),
                rs.getDate("delivery_date").toLocalDate(), rs.getString("notes"),
                rs.getObject("total_received_cost_minor", Long.class),
                rs.getObject("total_accepted_cost_minor", Long.class), rs.getString("currency_code"),
                rs.getTimestamp("confirmed_at").toInstant(), findLines(id)));
        return rows.stream().findFirst().orElseThrow(() -> new NoSuchElementException("Receipt not found"));
    }

    private List<LineResult> findLines(UUID receiptId) {
        return jdbc.query("""
                SELECT l.id, l.item_id, i.name AS item_name, i.unit AS base_unit,
                       l.purchase_unit, l.quantity_in_base_units, l.blisters_per_pack,
                       l.units_per_blister, l.received_quantity,
                       accepted_quantity, rejected_quantity, missing_quantity, accepted_base_quantity,
                       rejected_base_quantity, received_cost_minor, accepted_cost_minor,
                       batch_number, expiry_date, ordered_quantity,
                       unit_purchase_cost_minor, discrepancy_reason, notes
                FROM stock_receipt_line l
                JOIN item i ON i.id = l.item_id
                WHERE l.receipt_id = :id ORDER BY l.line_number
                """, new MapSqlParameterSource("id", receiptId), (rs, row) -> new LineResult(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("item_id"),
                rs.getString("item_name"), rs.getString("base_unit"), rs.getString("purchase_unit"),
                rs.getInt("quantity_in_base_units"), (Integer) rs.getObject("blisters_per_pack"),
                (Integer) rs.getObject("units_per_blister"), rs.getInt("received_quantity"),
                rs.getInt("accepted_quantity"), rs.getInt("rejected_quantity"),
                rs.getInt("missing_quantity"), rs.getInt("accepted_base_quantity"),
                rs.getInt("rejected_base_quantity"), rs.getObject("received_cost_minor", Long.class),
                rs.getObject("accepted_cost_minor", Long.class), rs.getString("batch_number"),
                rs.getDate("expiry_date").toLocalDate(),
                (Integer) rs.getObject("ordered_quantity"), rs.getObject("unit_purchase_cost_minor", Long.class),
                rs.getString("discrepancy_reason"), rs.getString("notes")));
    }

    public List<Receipt> recentForStore(UUID storeId) {
        List<UUID> ids = jdbc.query("""
                SELECT id FROM stock_receipt WHERE store_id = :storeId
                ORDER BY confirmed_at DESC LIMIT 100
                """, new MapSqlParameterSource("storeId", storeId),
                (rs, row) -> (UUID) rs.getObject("id"));
        return ids.stream().map(this::findById).toList();
    }

    private Receipt findByIdempotencyKey(String key) {
        List<UUID> ids = jdbc.query("SELECT id FROM stock_receipt WHERE idempotency_key = :key",
                new MapSqlParameterSource("key", key), (rs, row) -> (UUID) rs.getObject("id"));
        return ids.isEmpty() ? null : findById(ids.getFirst());
    }

    private void requireSameRequest(UUID id, String fingerprint) {
        String stored = jdbc.queryForObject("SELECT request_fingerprint FROM stock_receipt WHERE id = :id",
                new MapSqlParameterSource("id", id), String.class);
        if (!fingerprint.equals(stored)) {
            throw new IllegalStateException("Idempotency key was used for another receipt");
        }
    }

    private String fingerprint(StockReceiptRequest request) {
        try {
            byte[] json = mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot fingerprint stock receipt", ex);
        }
    }
}
