package com.adags.modules.inventory.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record RestockRequest(
        @NotNull UUID itemId,
        @NotNull UUID storeId,
        @NotBlank String batchNumber,
        @NotNull @Future LocalDate expiryDate,
        @Positive int quantity,
        @Min(0) long unitCostAmountMinor,
        String unitCostCurrencyCode,
        @NotNull UUID supplierId,
        @NotBlank String deliveryNoteRef,
        String notes
) {
}
