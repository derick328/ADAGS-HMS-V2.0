package com.adags.modules.billing.domain;

import java.util.UUID;

// 02-billing-architecture.md §10 / 18-admin.md §6.2: "Once payment has
// landed... that is a refund, not a void." Approval is blocked here, not
// silently converted — the admin must route the settled payment
// allocation(s) through the accountant's refund flow (Phase 7 item 1)
// first; once outstandingTotal is back to grossTotal, approving the same
// request succeeds.
public class InvoiceCancellationRequiresRefundException extends RuntimeException {

    private final UUID requestId;
    private final UUID invoiceId;

    public InvoiceCancellationRequiresRefundException(UUID requestId, UUID invoiceId) {
        super("Cannot approve cancellation request " + requestId + " — invoice " + invoiceId
                + " has a settled payment against it. Refund it first, then approve again.");
        this.requestId = requestId;
        this.invoiceId = invoiceId;
    }

    public UUID requestId() {
        return requestId;
    }

    public UUID invoiceId() {
        return invoiceId;
    }
}
