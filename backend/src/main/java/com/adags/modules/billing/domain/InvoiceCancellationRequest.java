package com.adags.modules.billing.domain;

import java.time.Instant;
import java.util.UUID;

// 18-admin.md §6/§6.1: receptionist raises a request with a reason; admin
// approves (VOID, or refund-first if paid) or rejects (back to ISSUED,
// live and collectable — "never left frozen after a decision").
// Reconsideration of a REJECTED request is a NEW row referencing the
// first via supersedesRequestId, never a reopened decision — "the
// sequence of requests and decisions stays in the record, which is what
// an auditor needs to see."
public record InvoiceCancellationRequest(
        UUID id,
        UUID invoiceId,
        UUID requestedBy,
        String reason,
        Instant requestedAt,
        CancellationRequestStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        UUID supersedesRequestId
) {

    public static InvoiceCancellationRequest raise(UUID invoiceId, String reason, UUID requestedBy, UUID supersedesRequestId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A cancellation reason is required.");
        }
        return new InvoiceCancellationRequest(null, invoiceId, requestedBy, reason, null,
                CancellationRequestStatus.PENDING, null, null, null, supersedesRequestId);
    }
}
