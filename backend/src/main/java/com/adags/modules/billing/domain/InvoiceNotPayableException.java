package com.adags.modules.billing.domain;

import java.util.UUID;

// 02-billing-architecture.md §9.2: "Payment against a VOID or
// CANCELLATION_PENDING invoice is rejected."
public class InvoiceNotPayableException extends RuntimeException {

    public InvoiceNotPayableException(UUID invoiceId, InvoiceState state) {
        super("Invoice " + invoiceId + " is " + state + " — payment is rejected.");
    }
}
