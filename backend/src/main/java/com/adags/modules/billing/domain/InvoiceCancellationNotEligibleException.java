package com.adags.modules.billing.domain;

import java.util.UUID;

// Thrown by InvoiceRepository.markCancellationPending (invoice not
// ISSUED) and returnToIssuedFromCancellationPending (invoice not
// CANCELLATION_PENDING) — the WHERE-clause guard behind each transition.
public class InvoiceCancellationNotEligibleException extends RuntimeException {

    private final UUID invoiceId;
    private final InvoiceState state;

    public InvoiceCancellationNotEligibleException(UUID invoiceId, InvoiceState state) {
        super("Invoice " + invoiceId + " is " + state + " — not eligible for this cancellation transition.");
        this.invoiceId = invoiceId;
        this.state = state;
    }

    public UUID invoiceId() {
        return invoiceId;
    }

    public InvoiceState state() {
        return state;
    }
}
