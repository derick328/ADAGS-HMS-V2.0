package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.StockValuationRow;

import java.util.UUID;

public record StockValuationResponse(UUID storeId, String storeName, long totalValueAmountMinor, String currencyCode) {

    public static StockValuationResponse from(StockValuationRow row) {
        return new StockValuationResponse(row.storeId(), row.storeName(), row.totalValue().amountMinor(),
                row.totalValue().currencyCode());
    }
}
