package com.adags.modules.billing.domain;

import java.util.UUID;

// 02-billing-architecture.md §10: "Once any payment has landed, void is
// blocked" — a partially or fully paid invoice must go through the
// accountant's cancellation-request flow instead (not built yet). This
// path is only for the fully-unpaid case: an encounter's charge voided
// because the patient left without paying anything against it.
public class InvoiceCannotBeVoidedException extends RuntimeException {

    public InvoiceCannotBeVoidedException(UUID invoiceId) {
        super("Invoice " + invoiceId + " cannot be voided this way — it is not ISSUED or has a settled amount against it.");
    }
}
