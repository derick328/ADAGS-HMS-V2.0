package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.StockValuationRow;

import java.util.UUID;

public record StockValuationResponse(UUID storeId, String storeName, Long totalValueAmountMinor, String currencyCode) {

    public static StockValuationResponse from(StockValuationRow row) {
        return new StockValuationResponse(row.storeId(), row.storeName(),
                row.totalValue() == null ? null : row.totalValue().amountMinor(),
                row.totalValue() == null ? "TZS" : row.totalValue().currencyCode());
    }
}
