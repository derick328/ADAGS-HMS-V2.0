package com.adags.modules.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StockReceiptRequest(
        @NotNull UUID supplierId,
        @NotNull UUID storeId,
        @NotBlank String invoiceNumber,
        @NotBlank String deliveryNoteNumber,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate deliveryDate,
        String notes,
        @NotBlank String idempotencyKey,
        @NotEmpty List<@Valid Line> lines
) {
    public record Line(
            @NotNull UUID itemId,
            @NotBlank String purchaseUnit,
            @Positive Integer orderedQuantity,
            @PositiveOrZero int receivedQuantity,
            @PositiveOrZero int acceptedQuantity,
            @PositiveOrZero int rejectedQuantity,
            @NotBlank String batchNumber,
            @NotNull LocalDate expiryDate,
            @PositiveOrZero Long unitPurchaseCostMinor,
            String discrepancyReason,
            String notes,
            @Positive Integer blistersPerPack,
            @Positive Integer unitsPerBlister
    ) {
        public Line(UUID itemId, String purchaseUnit, Integer orderedQuantity, int receivedQuantity,
                    int acceptedQuantity, int rejectedQuantity, String batchNumber, LocalDate expiryDate,
                    Long unitPurchaseCostMinor, String discrepancyReason, String notes) {
            this(itemId, purchaseUnit, orderedQuantity, receivedQuantity, acceptedQuantity, rejectedQuantity,
                    batchNumber, expiryDate, unitPurchaseCostMinor, discrepancyReason, notes, null, null);
        }
    }
}
