package com.adags.modules.inventory.application;

import com.adags.shared.kernel.Money;

import java.time.LocalDate;
import java.util.UUID;

public record RestockCommand(
        UUID itemId,
        UUID storeId,
        String batchNumber,
        LocalDate expiryDate,
        int quantity,
        Money unitCost,
        UUID supplierId,
        String deliveryNoteRef,
        String notes,
        UUID actorUserId
) {
}
