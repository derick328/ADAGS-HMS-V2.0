package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.DispenseLine;

import java.util.UUID;

public record DispenseLineResponse(
        UUID id, UUID itemId, UUID batchId, double quantity, Long unitCostAmountMinor, String unitCostCurrencyCode,
        String fefoOverrideReason, double requestedQuantity, String requestedUnit, double conversionFactor
) {

    public static DispenseLineResponse from(DispenseLine line) {
        return new DispenseLineResponse(line.id(), line.itemId(), line.batchId(), line.quantity(),
                line.costKnown() ? line.unitCost().amountMinor() : null,
                line.unitCost().currencyCode(), line.fefoOverrideReason(),
                line.requestedQuantity(), line.requestedUnit(), line.conversionFactor());
    }
}
