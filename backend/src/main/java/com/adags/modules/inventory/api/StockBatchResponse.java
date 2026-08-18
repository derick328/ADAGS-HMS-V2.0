package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchStatus;

import java.time.LocalDate;
import java.util.UUID;

public record StockBatchResponse(
        UUID id,
        UUID itemId,
        UUID storeId,
        String batchNumber,
        LocalDate expiryDate,
        long unitCostAmountMinor,
        String unitCostCurrencyCode,
        UUID supplierId,
        String deliveryNoteRef,
        StockBatchStatus status
) {

    public static StockBatchResponse from(StockBatch batch) {
        return new StockBatchResponse(batch.id(), batch.itemId(), batch.storeId(), batch.batchNumber(),
                batch.expiryDate(), batch.unitCost().amountMinor(), batch.unitCost().currencyCode(),
                batch.supplierId(), batch.deliveryNoteRef(), batch.status());
    }
}
