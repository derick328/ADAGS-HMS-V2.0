package com.adags.modules.billing.domain;

import java.util.UUID;

// A credit note may not be issued against a DRAFT (no locked lines to
// correct yet), VOID (already reversed), or CANCELLATION_PENDING (frozen
// pending the admin's cancellation decision) invoice.
public class CreditNoteNotEligibleException extends RuntimeException {

    private final UUID invoiceId;
    private final InvoiceState state;

    public CreditNoteNotEligibleException(UUID invoiceId, InvoiceState state) {
        super("Invoice " + invoiceId + " is " + state + " — not eligible for a credit note.");
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
