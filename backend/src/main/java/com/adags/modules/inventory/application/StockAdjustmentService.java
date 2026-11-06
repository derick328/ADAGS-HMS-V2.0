package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.MovementType;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.StockBalanceRepository;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StockMovement;
import com.adags.modules.inventory.domain.StockMovementRepository;
import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class StockAdjustmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final StockBatchRepository batches;
    private final ItemRepository items;
    private final StockBalanceRepository balances;
    private final StockMovementRepository movements;
    private final AuditRecorder audit;
    private final DomainEventPublisher events;

    public StockAdjustmentService(NamedParameterJdbcTemplate jdbc, StockBatchRepository batches,
                                  ItemRepository items,
                                  StockBalanceRepository balances, StockMovementRepository movements,
                                  AuditRecorder audit, DomainEventPublisher events) {
        this.jdbc = jdbc;
        this.batches = batches;
        this.items = items;
        this.balances = balances;
        this.movements = movements;
        this.audit = audit;
        this.events = events;
    }

    public record Adjustment(UUID id, UUID batchId, UUID itemId, UUID storeId, String type,
                             int quantityDelta, String reason, String status, UUID requestedBy,
                             Instant requestedAt, UUID reviewedBy, Instant reviewedAt, String reviewReason) {
    }

    @Transactional
    public Adjustment request(UUID batchId, String type, int delta, String reason,
                              String idempotencyKey, UUID actorId) {
        if (reason == null || reason.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Reason and idempotency key are required");
        }
        if (type == null || !List.of("DAMAGED", "EXPIRED", "LOST", "PHYSICAL_COUNT",
                "RECEIPT_CORRECTION", "QUARANTINE").contains(type)) {
            throw new IllegalArgumentException("Unsupported adjustment type");
        }
        if (type.equals("QUARANTINE") && delta != 0 || !type.equals("QUARANTINE") && delta == 0 ||
                List.of("DAMAGED", "EXPIRED", "LOST").contains(type) && delta >= 0) {
            throw new IllegalArgumentException("Invalid quantity change for adjustment type");
        }
        var batch = batches.findById(batchId).orElseThrow(() -> new NoSuchElementException("Batch not found"));
        if (delta > 0) assertMayAddStock(batch.itemId());
        UUID id = UUID.randomUUID();
        List<UUID> inserted = jdbc.query("""
                INSERT INTO stock_adjustment
                  (id, batch_id, item_id, store_id, adjustment_type, quantity_delta,
                   reason, requested_by, idempotency_key)
                VALUES (:id, :batchId, :itemId, :storeId, :type, :delta,
                        :reason, :actorId, :key)
                ON CONFLICT (idempotency_key) DO NOTHING RETURNING id
                """, new MapSqlParameterSource().addValue("id", id).addValue("batchId", batchId)
                .addValue("itemId", batch.itemId()).addValue("storeId", batch.storeId())
                .addValue("type", type).addValue("delta", delta).addValue("reason", reason.trim())
                .addValue("actorId", actorId).addValue("key", idempotencyKey),
                (rs, row) -> (UUID) rs.getObject("id"));
        if (inserted.isEmpty()) {
            Adjustment existing = findByKey(idempotencyKey);
            if (!existing.batchId().equals(batchId) || !existing.type().equals(type)
                    || existing.quantityDelta() != delta || !existing.reason().equals(reason.trim())) {
                throw new IllegalStateException("Idempotency key belongs to another adjustment");
            }
            return existing;
        }
        audit.record(actorId, "STOCK_ADJUSTMENT_REQUEST", "STOCK_ADJUSTMENT", id, null);
        events.publish("StockAdjustmentRequested", java.util.Map.of("adjustmentId", id.toString()));
        return findById(id);
    }

    @Transactional
    public Adjustment review(UUID id, boolean approve, String reviewReason, UUID actorId) {
        Adjustment adjustment = jdbc.query("""
                SELECT id, batch_id, item_id, store_id, adjustment_type, quantity_delta,
                       reason, status, requested_by, requested_at, reviewed_by, reviewed_at, review_reason
                FROM stock_adjustment WHERE id = :id FOR UPDATE
                """, new MapSqlParameterSource("id", id), this::map).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Adjustment not found"));
        if (!adjustment.status().equals("PENDING")) {
            throw new IllegalStateException("Adjustment has already been reviewed");
        }
        if (actorId.equals(adjustment.requestedBy())) {
            throw new IllegalArgumentException("A second person must approve a stock adjustment");
        }
        if (!approve && (reviewReason == null || reviewReason.isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        if (approve) {
            if (adjustment.quantityDelta() > 0) assertMayAddStock(adjustment.itemId());
            if (adjustment.type().equals("QUARANTINE")) {
                jdbc.update("UPDATE stock_batch SET status = 'QUARANTINED' WHERE id = :id",
                        new MapSqlParameterSource("id", adjustment.batchId()));
            } else {
                balances.applyDelta(adjustment.itemId(), adjustment.batchId(),
                        adjustment.storeId(), adjustment.quantityDelta());
                MovementType movementType = adjustment.quantityDelta() < 0
                        && List.of("DAMAGED", "EXPIRED", "LOST").contains(adjustment.type())
                        ? MovementType.WRITE_OFF : MovementType.ADJUSTMENT;
                movements.insert(StockMovement.record(adjustment.itemId(), adjustment.batchId(),
                        adjustment.storeId(), adjustment.quantityDelta(), movementType,
                        "STOCK_ADJUSTMENT", id, actorId));
                Integer remaining = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(quantity), 0) FROM stock_balance WHERE batch_id = :id
                        """, new MapSqlParameterSource("id", adjustment.batchId()), Integer.class);
                if (adjustment.quantityDelta() < 0 && remaining != null && remaining == 0) {
                    jdbc.update("UPDATE stock_batch SET status = 'WRITTEN_OFF' WHERE id = :id",
                            new MapSqlParameterSource("id", adjustment.batchId()));
                }
            }
        }
        jdbc.update("""
                UPDATE stock_adjustment SET status = :status, reviewed_by = :actorId,
                  reviewed_at = now(), review_reason = :reason WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", id)
                .addValue("status", approve ? "APPROVED" : "REJECTED")
                .addValue("actorId", actorId).addValue("reason", reviewReason));
        audit.record(actorId, approve ? "STOCK_ADJUSTMENT_APPROVE" : "STOCK_ADJUSTMENT_REJECT",
                "STOCK_ADJUSTMENT", id, null);
        events.publish(approve ? "StockAdjustmentApproved" : "StockAdjustmentRejected",
                java.util.Map.of("adjustmentId", id.toString()));
        return findById(id);
    }

    public Adjustment findById(UUID id) {
        return jdbc.query("""
                SELECT id, batch_id, item_id, store_id, adjustment_type, quantity_delta,
                       reason, status, requested_by, requested_at, reviewed_by, reviewed_at, review_reason
                FROM stock_adjustment WHERE id = :id
                """, new MapSqlParameterSource("id", id), this::map).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Adjustment not found"));
    }

    public List<Adjustment> forStore(UUID storeId) {
        return jdbc.query("""
                SELECT id, batch_id, item_id, store_id, adjustment_type, quantity_delta,
                       reason, status, requested_by, requested_at, reviewed_by, reviewed_at, review_reason
                FROM stock_adjustment WHERE store_id = :storeId ORDER BY requested_at DESC LIMIT 100
                """, new MapSqlParameterSource("storeId", storeId), this::map);
    }

    private Adjustment findByKey(String key) {
        UUID id = jdbc.queryForObject("SELECT id FROM stock_adjustment WHERE idempotency_key = :key",
                new MapSqlParameterSource("key", key), UUID.class);
        return findById(id);
    }

    private void assertMayAddStock(UUID itemId) {
        var item = items.findById(itemId).orElseThrow(() -> new NoSuchElementException("Item not found"));
        item.assertDispensable();
        if (!item.active()) throw new IllegalArgumentException("Inactive medicine cannot gain usable stock");
    }

    private Adjustment map(ResultSet rs, int row) throws SQLException {
        return new Adjustment((UUID) rs.getObject("id"), (UUID) rs.getObject("batch_id"),
                (UUID) rs.getObject("item_id"), (UUID) rs.getObject("store_id"),
                rs.getString("adjustment_type"), rs.getInt("quantity_delta"), rs.getString("reason"),
                rs.getString("status"), (UUID) rs.getObject("requested_by"),
                rs.getTimestamp("requested_at").toInstant(), (UUID) rs.getObject("reviewed_by"),
                rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                rs.getString("review_reason"));
    }
}
