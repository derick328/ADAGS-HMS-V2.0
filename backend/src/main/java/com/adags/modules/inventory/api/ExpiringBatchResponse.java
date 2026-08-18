package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.ExpiringBatchRow;

import java.time.LocalDate;
import java.util.UUID;

public record ExpiringBatchResponse(
        UUID batchId, UUID itemId, String itemCode, String itemName, String batchNumber, LocalDate expiryDate, int quantity
) {

    public static ExpiringBatchResponse from(ExpiringBatchRow row) {
        return new ExpiringBatchResponse(row.batchId(), row.itemId(), row.itemCode(), row.itemName(),
                row.batchNumber(), row.expiryDate(), row.quantity());
    }
}
