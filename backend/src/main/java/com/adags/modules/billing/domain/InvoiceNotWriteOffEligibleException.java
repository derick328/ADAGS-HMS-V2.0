package com.adags.modules.billing.domain;

import java.util.UUID;

// A write-off may only be requested against a post-discharge receivable —
// the whole point is collecting on a balance discharge could not block on
// (root CLAUDE.md "Billing"). An ISSUED invoice still has a live patient to
// collect from through the ordinary payment path.
public class InvoiceNotWriteOffEligibleException extends RuntimeException {

    private final UUID invoiceId;
    private final InvoiceState state;

    public InvoiceNotWriteOffEligibleException(UUID invoiceId, InvoiceState state) {
        super("Invoice " + invoiceId + " is " + state + ", not RECEIVABLE_POST_DISCHARGE — not eligible for write-off.");
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
