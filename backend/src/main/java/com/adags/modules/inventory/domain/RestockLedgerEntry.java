package com.adags.modules.inventory.domain;

import java.time.Instant;
import java.util.UUID;

// 18-admin.md §8 "Medication & pharmacy": the admin's medication-and-pharmacy
// expense screen shows every pharmacist restock as a spend row without the
// admin re-entering anything the pharmacist already captured on the receive
// form. This is a joined read model over stock_batch + stock_movement(RECEIPT)
// + item + supplier + app_user — never a materialised table, so a corrected
// pharmacy receipt never leaves a stale expense figure behind.
//
// totalCostMinor is unitCostAmountMinor * receivedQuantity — the money that
// actually left the facility for this restock, not the per-unit sticker.
public record RestockLedgerEntry(
        UUID batchId,
        UUID itemId,
        String itemName,
        String batchNumber,
        int receivedQuantity,
        long unitCostAmountMinor,
        long totalCostMinor,
        String currencyCode,
        UUID supplierId,
        String supplierName,
        String deliveryNoteRef,
        Instant receivedAt,
        UUID receivedById,
        String receivedByName
) {
}
