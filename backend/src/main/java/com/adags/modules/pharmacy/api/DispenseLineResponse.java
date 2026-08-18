package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.DispenseLine;

import java.util.UUID;

public record DispenseLineResponse(
        UUID id, UUID itemId, UUID batchId, int quantity, long unitCostAmountMinor, String unitCostCurrencyCode,
        String fefoOverrideReason
) {

    public static DispenseLineResponse from(DispenseLine line) {
        return new DispenseLineResponse(line.id(), line.itemId(), line.batchId(), line.quantity(),
                line.unitCost().amountMinor(), line.unitCost().currencyCode(), line.fefoOverrideReason());
    }
}
