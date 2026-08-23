package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.PayerCategory;

import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID encounterId,
        UUID patientId,
        InvoiceState state,
        PayerCategory payerCategory,
        String invoiceNumber,
        long grossTotalMinor,
        long insurancePortionTotalMinor,
        long patientPortionTotalMinor,
        long outstandingTotalMinor,
        String currencyCode,
        int version,
        Instant createdAt
) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(invoice.id(), invoice.encounterId(), invoice.patientId(), invoice.state(),
                invoice.payerCategory(), invoice.invoiceNumber(), invoice.grossTotal().amountMinor(),
                invoice.insurancePortionTotal().amountMinor(), invoice.patientPortionTotal().amountMinor(),
                invoice.outstandingTotal().amountMinor(), invoice.grossTotal().currencyCode(), invoice.version(),
                invoice.createdAt());
    }
}
