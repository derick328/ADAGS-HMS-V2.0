package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.CurrentStockRow;

import java.time.LocalDate;
import java.util.UUID;

public record CurrentStockResponse(
        UUID itemId, String itemCode, String itemName, UUID batchId, String batchNumber, LocalDate expiryDate,
        UUID storeId, String storeName, int quantity,
        String strength, String baseUnit, String stockStatus, int availableQuantity
) {

    public static CurrentStockResponse from(CurrentStockRow row) {
        return new CurrentStockResponse(row.itemId(), row.itemCode(), row.itemName(), row.batchId(),
                row.batchNumber(), row.expiryDate(), row.storeId(), row.storeName(), row.quantity(),
                row.strength(), row.baseUnit(), row.stockStatus(), row.availableQuantity());
    }
}
