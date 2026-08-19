package com.adags.modules.billing.domain;

import java.util.UUID;

// 18-admin.md §6: "One PENDING request per invoice at a time" — enforced
// by a partial unique index (invoice_cancellation_request.invoice_id
// WHERE status = 'PENDING'), not just this pre-check (root CLAUDE.md
// "Concurrency": the database constraint is the real guard for two
// requests racing against the same invoice).
public class InvoiceAlreadyHasPendingCancellationException extends RuntimeException {

    private final UUID invoiceId;

    public InvoiceAlreadyHasPendingCancellationException(UUID invoiceId) {
        super("Invoice " + invoiceId + " already has a pending cancellation request.");
        this.invoiceId = invoiceId;
    }

    public UUID invoiceId() {
        return invoiceId;
    }
}
