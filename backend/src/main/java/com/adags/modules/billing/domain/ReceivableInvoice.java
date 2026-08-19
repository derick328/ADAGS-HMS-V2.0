package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// One row of the accountant's post-discharge receivables worklist — the
// detail behind a ReceivableSummary cell, and what a write-off request
// selects against.
public record ReceivableInvoice(
        UUID invoiceId,
        String invoiceNumber,
        UUID patientId,
        UUID encounterId,
        PayerCategory payerCategory,
        Money outstandingTotal,
        Instant receivableAt,
        long agingDays,
        AgingBucket bucket
) {
}
