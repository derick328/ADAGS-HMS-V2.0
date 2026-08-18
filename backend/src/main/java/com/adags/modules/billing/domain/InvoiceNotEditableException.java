package com.adags.modules.billing.domain;

import java.util.UUID;

// 02-billing-architecture.md §10: "Lines locked" once ISSUED. Enforced in
// JdbcInvoiceLineRepository.insert's WHERE clause (invoice must still be
// DRAFT) rather than a read-then-write check in application code, so a
// stale read can't slip a line onto an already-issued, already-numbered
// invoice.
public class InvoiceNotEditableException extends RuntimeException {

    public InvoiceNotEditableException(UUID invoiceId) {
        super("Invoice " + invoiceId + " is not DRAFT — lines are locked once an invoice is issued.");
    }
}
