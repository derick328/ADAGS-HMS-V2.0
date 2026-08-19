package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.AgingBucket;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.ReceivableInvoice;

import java.time.Instant;
import java.util.UUID;

public record ReceivableInvoiceResponse(
        UUID invoiceId,
        String invoiceNumber,
        UUID patientId,
        UUID encounterId,
        PayerCategory payerCategory,
        long outstandingTotalMinor,
        String currencyCode,
        Instant receivableAt,
        long agingDays,
        AgingBucket bucket
) {

    public static ReceivableInvoiceResponse from(ReceivableInvoice invoice) {
        return new ReceivableInvoiceResponse(invoice.invoiceId(), invoice.invoiceNumber(), invoice.patientId(),
                invoice.encounterId(), invoice.payerCategory(), invoice.outstandingTotal().amountMinor(),
                invoice.outstandingTotal().currencyCode(), invoice.receivableAt(), invoice.agingDays(), invoice.bucket());
    }
}
