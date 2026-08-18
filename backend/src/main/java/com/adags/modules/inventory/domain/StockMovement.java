package com.adags.modules.inventory.domain;

import java.time.Instant;
import java.util.UUID;

// root CLAUDE.md "Data": append-only ledger row. Never updated, never
// deleted — StockMovementRepository exposes insert() and reads only.
public record StockMovement(
        UUID id,
        UUID itemId,
        UUID batchId,
        UUID storeId,
        int qtyDelta,
        MovementType movementType,
        String referenceType,
        UUID referenceId,
        UUID actorId,
        Instant occurredAt
) {

    public static StockMovement record(UUID itemId, UUID batchId, UUID storeId, int qtyDelta,
                                        MovementType movementType, String referenceType, UUID referenceId,
                                        UUID actorId) {
        if (qtyDelta == 0) {
            throw new IllegalArgumentException("qtyDelta must not be zero.");
        }
        return new StockMovement(null, itemId, batchId, storeId, qtyDelta, movementType, referenceType,
                referenceId, actorId, null);
    }
}
