package com.adags.modules.billing.domain;

import java.util.UUID;

// Thrown by InvoiceRepository.recordWriteOff when the invoice is not
// RECEIVABLE_POST_DISCHARGE, or amount exceeds the outstanding balance at
// the moment of the update — including the case where a concurrent
// write-off already consumed the balance this one was validated against.
public class InvoiceWriteOffExceedsOutstandingException extends RuntimeException {

    private final UUID invoiceId;

    public InvoiceWriteOffExceedsOutstandingException(UUID invoiceId) {
        super("Write-off for invoice " + invoiceId
                + " could not be applied — the invoice is not a post-discharge receivable, "
                + "or the amount exceeds its current outstanding balance.");
        this.invoiceId = invoiceId;
    }

    public UUID invoiceId() {
        return invoiceId;
    }
}
