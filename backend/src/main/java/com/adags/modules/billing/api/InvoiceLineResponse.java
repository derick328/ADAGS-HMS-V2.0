package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.InvoiceLine;

import java.util.UUID;

public record InvoiceLineResponse(
        UUID id,
        UUID serviceItemId,
        String serviceItemName,
        int quantity,
        long grossAmountMinor,
        long insurancePortionMinor,
        long patientPortionMinor,
        /**
         * patientPortionMinor minus what settled payments have already
         * covered — 03-billing-addendum.md §1: "a line's gate opens when
         * allocated amount covers its patient portion," so the line-picker
         * payment screen must tick this amount, not the full patient
         * portion, for a line that already carries a partial settlement.
         */
        long remainingMinor,
        String currencyCode,
        UUID authorizationId
) {

    public static InvoiceLineResponse from(InvoiceLine line, String serviceItemName, long settledMinor) {
        long remaining = Math.max(0, line.patientPortion().amountMinor() - settledMinor);
        return new InvoiceLineResponse(line.id(), line.serviceItemId(), serviceItemName, line.quantity(),
                line.grossAmount().amountMinor(), line.insurancePortion().amountMinor(),
                line.patientPortion().amountMinor(), remaining, line.grossAmount().currencyCode(), line.authorizationId());
    }
}
