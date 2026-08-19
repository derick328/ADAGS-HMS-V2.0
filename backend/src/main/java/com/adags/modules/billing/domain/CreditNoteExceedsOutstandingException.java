package com.adags.modules.billing.domain;

import java.util.UUID;

// Thrown by InvoiceRepository.recordCreditNote when the invoice is not
// eligible, or amount exceeds the outstanding balance at the moment of the
// update — including a concurrent credit note or write-off having already
// consumed the balance this one was validated against.
public class CreditNoteExceedsOutstandingException extends RuntimeException {

    private final UUID invoiceId;

    public CreditNoteExceedsOutstandingException(UUID invoiceId) {
        super("Credit note for invoice " + invoiceId
                + " could not be applied — the invoice is not eligible, "
                + "or the amount exceeds its current outstanding balance.");
        this.invoiceId = invoiceId;
    }

    public UUID invoiceId() {
        return invoiceId;
    }
}
