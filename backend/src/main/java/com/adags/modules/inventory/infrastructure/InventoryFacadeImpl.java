package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.inventory.application.MedicinePackagingService;
import com.adags.modules.inventory.domain.BatchExpiredException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.MovementType;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.modules.inventory.domain.StockBalanceRepository;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StockBatchStatus;
import com.adags.modules.inventory.domain.StockMovement;
import com.adags.modules.inventory.domain.StockMovementRepository;
import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.shared.kernel.BatchAvailability;
import com.adags.shared.kernel.ItemSummary;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Component
public class InventoryFacadeImpl implements InventoryFacade {
    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    private final ItemRepository itemRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StoreRepository storeRepository;
    private final MedicinePackagingService packagingService;

    public InventoryFacadeImpl(
            ItemRepository itemRepository,
            StockBatchRepository stockBatchRepository,
            StockBalanceRepository stockBalanceRepository,
            StockMovementRepository stockMovementRepository,
            StoreRepository storeRepository,
            MedicinePackagingService packagingService
    ) {
        this.itemRepository = itemRepository;
        this.stockBatchRepository = stockBatchRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.storeRepository = storeRepository;
        this.packagingService = packagingService;
    }

    @Override
    public boolean itemExists(UUID itemId) {
        return itemRepository.findById(itemId).isPresent();
    }

    @Override
    @Transactional
    public UUID ensureCatalogueItem(String code, String name, String classification, UUID actorUserId) {
        return itemRepository.findByCode(code)
                .map(Item::id)
                .orElseGet(() -> {
                    String cls = (classification == null || classification.isBlank()) ? "General" : classification;
                    Item draft = Item.create(code, name, cls, "—", null, "unit", 0, actorUserId);
                    Item inserted = itemRepository.insert(draft);
                    // create() lands the row UNCLASSIFIED + inactive; move it
                    // straight to PRESCRIPTION_ONLY + active so a doctor can
                    // prescribe without waiting on a manual classify step
                    // (V72 backfill reasoning applied to fresh imports).
                    itemRepository.updateSchedule(inserted.id(), RegulatorySchedule.PRESCRIPTION_ONLY);
                    itemRepository.activate(inserted.id());
                    return inserted.id();
                });
    }

    @Override
    public boolean isDispensable(UUID itemId) {
        return itemRepository.findById(itemId)
                .map(item -> item.active() && item.regulatorySchedule().isDispensable())
                .orElse(false);
    }

    @Override
    public Optional<ItemSummary> findItemSummary(UUID itemId) {
        return itemRepository.findById(itemId)
                .map(item -> new ItemSummary(item.id(), item.code(), item.name(), item.unit(),
                        item.classification(), item.form(),
                        item.regulatorySchedule().isDispensable(), item.active(),
                        item.requiresAdministrationWitness()));
    }

    @Override
    public int conversionFactor(UUID itemId, String unit) {
        return packagingService.approved(itemId, unit).factor();
    }

    @Override
    public List<BatchAvailability> findFefoBatches(UUID itemId) {
        return stockBatchRepository.findActiveByItemFefo(itemId).stream()
                .map(batch -> new BatchAvailability(batch.id(), batch.itemId(), batch.batchNumber(),
                        batch.expiryDate(), stockBalanceRepository.quantityOnHand(batch.id())))
                .toList();
    }

    @Override
    public List<BatchAvailability> findFefoBatches(UUID itemId, UUID facilityId) {
        return stockBatchRepository.findActiveByItemFefo(itemId, facilityId).stream()
                .map(batch -> new BatchAvailability(batch.id(), batch.itemId(), batch.batchNumber(),
                        batch.expiryDate(), stockBalanceRepository.quantityOnHand(batch.id())))
                .toList();
    }

    @Override
    public void assertBatchFacility(UUID batchId, UUID facilityId) {
        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));
        if (storeRepository.findById(batch.storeId())
                .map(store -> !store.facilityId().equals(facilityId)).orElse(true)) {
            throw new IllegalArgumentException("Batch is outside the prescription facility");
        }
    }

    @Override
    public Optional<BatchAvailability> findBatch(UUID batchId) {
        return stockBatchRepository.findById(batchId)
                .map(batch -> new BatchAvailability(batch.id(), batch.itemId(), batch.batchNumber(),
                        batch.expiryDate(), stockBalanceRepository.quantityOnHand(batch.id())));
    }

    @Override
    public void assertDispensePackaging(UUID itemId, Map<UUID, Integer> quantityByBatch) {
        Map<UUID, Integer> grouped = new HashMap<>();
        Map<UUID, Integer> multiples = new HashMap<>();
        for (var entry : quantityByBatch.entrySet()) {
            StockBatch batch = stockBatchRepository.findById(entry.getKey())
                    .orElseThrow(() -> new NoSuchElementException("Batch not found: " + entry.getKey()));
            if (!batch.itemId().equals(itemId)) {
                throw new IllegalArgumentException("Batch belongs to another medicine");
            }
            var rule = stockBatchRepository.packagingRule(batch.id());
            if (rule.dispenseMultipleBase() <= 1) continue;
            UUID group = rule.receiptLineId() == null ? batch.id() : rule.receiptLineId();
            grouped.merge(group, entry.getValue(), Math::addExact);
            multiples.put(group, rule.dispenseMultipleBase());
        }
        for (var entry : grouped.entrySet()) {
            if (entry.getValue() % multiples.get(entry.getKey()) != 0) {
                throw new IllegalArgumentException("This medicine cannot be split from its approved package");
            }
        }
    }

    @Override
    @Transactional
    public Money decrementForDispense(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        // Evaluated live, same discipline as the billing gate — the item's
        // regulatory_schedule could have changed to CONTROLLED since it
        // was prescribed.
        item.assertDispensable();

        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));
        if (batch.status() != StockBatchStatus.ACTIVE || batch.isExpiredAsOf(LocalDate.now(FACILITY_ZONE))) {
            // 12-pharmacist.md §6.3: hard-blocked, not flagged — checked
            // against the calendar every time, not only the stored status
            // column, since no scheduled job flips it the instant a batch
            // expires (§7.3 is future work).
            throw new BatchExpiredException(batchId);
        }

        stockBalanceRepository.applyDelta(itemId, batchId, batch.storeId(), -quantity);

        stockMovementRepository.insert(StockMovement.record(itemId, batchId, batch.storeId(), -quantity,
                MovementType.DISPENSE, "DISPENSE_LINE", referenceId, actorUserId));

        return batch.unitCost();
    }

    @Override
    @Transactional
    public Money decrementForProcedureItem(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        item.assertDispensable();

        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));
        if (batch.status() != StockBatchStatus.ACTIVE || batch.isExpiredAsOf(LocalDate.now(FACILITY_ZONE))) {
            throw new BatchExpiredException(batchId);
        }

        stockBalanceRepository.applyDelta(itemId, batchId, batch.storeId(), -quantity);

        stockMovementRepository.insert(StockMovement.record(itemId, batchId, batch.storeId(), -quantity,
                MovementType.DISPENSE, "PROCEDURE_ITEM", referenceId, actorUserId));

        return batch.unitCost();
    }

    @Override
    @Transactional
    public void incrementForReturn(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId) {
        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));

        stockBalanceRepository.applyDelta(itemId, batchId, batch.storeId(), quantity);

        stockMovementRepository.insert(StockMovement.record(itemId, batchId, batch.storeId(), quantity,
                MovementType.RETURN, "PROCEDURE_ITEM_RETURN", referenceId, actorUserId));
    }
}
