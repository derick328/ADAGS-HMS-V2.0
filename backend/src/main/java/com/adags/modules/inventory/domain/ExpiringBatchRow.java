package com.adags.modules.inventory.domain;

import java.time.LocalDate;
import java.util.UUID;

// 12-pharmacist.md §6.1/§7.3: batches expiring within the protocol-settable
// window, earliest first — the watchlist a pharmacist works down before
// stock is written off.
public record ExpiringBatchRow(
        UUID batchId, UUID itemId, String itemCode, String itemName, String batchNumber, LocalDate expiryDate, int quantity
) {
}
