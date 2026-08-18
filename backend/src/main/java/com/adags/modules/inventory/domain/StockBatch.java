package com.adags.modules.inventory.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 12-pharmacist.md §7.2/§12 (D3, D6): batch_number and delivery_note_ref
// are the paper trail on a stock increase; unit_cost lives here, never on
// Item, because the same drug is bought at different prices on different
// deliveries. Quantity is deliberately absent — the received quantity is
// only ever expressed as a StockMovement(RECEIPT) row, never a column a
// caller could overwrite (root CLAUDE.md "Data").
public record StockBatch(
        UUID id,
        UUID itemId,
        UUID storeId,
        String batchNumber,
        LocalDate expiryDate,
        Money unitCost,
        UUID supplierId,
        String deliveryNoteRef,
        StockBatchStatus status,
        UUID receivedBy,
        Instant receivedAt,
        String notes
) {

    public static StockBatch receive(UUID itemId, UUID storeId, String batchNumber, LocalDate expiryDate,
                                      Money unitCost, UUID supplierId, String deliveryNoteRef, UUID receivedBy,
                                      String notes) {
        if (batchNumber == null || batchNumber.isBlank()) {
            throw new IllegalArgumentException("batchNumber is required.");
        }
        if (deliveryNoteRef == null || deliveryNoteRef.isBlank()) {
            throw new IllegalArgumentException("deliveryNoteRef is required.");
        }
        return new StockBatch(null, itemId, storeId, batchNumber, expiryDate, unitCost, supplierId,
                deliveryNoteRef, StockBatchStatus.ACTIVE, receivedBy, null, notes);
    }

    public boolean isExpiredAsOf(LocalDate asOf) {
        return !expiryDate.isAfter(asOf);
    }
}
