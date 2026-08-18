package com.adags.modules.inventory.domain;

import java.time.LocalDate;
import java.util.UUID;

// 12-pharmacist.md §6.1: "Current stock" — one row per (item, batch, store)
// with quantity on hand.
public record CurrentStockRow(
        UUID itemId, String itemCode, String itemName, UUID batchId, String batchNumber,
        LocalDate expiryDate, UUID storeId, String storeName, int quantity
) {
}
