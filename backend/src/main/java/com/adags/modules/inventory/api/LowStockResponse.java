package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.LowStockRow;

import java.util.UUID;

public record LowStockResponse(UUID itemId, String itemCode, String itemName, int totalQuantity, int reorderLevel) {

    public static LowStockResponse from(LowStockRow row) {
        return new LowStockResponse(row.itemId(), row.itemCode(), row.itemName(), row.totalQuantity(), row.reorderLevel());
    }
}
