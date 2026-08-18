package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.PaymentMethod;

import java.util.List;
import java.util.UUID;

// 03-billing-addendum.md §1: "The payment screen is a line picker. The
// receptionist ticks which lines the money is for; the total fills in."
// allocations is that tick list — the total is derived from it, never
// entered separately. The open cashier shift is resolved from actorUserId
// (the acting cashier), not passed explicitly.
public record PostPaymentCommand(
        UUID patientId,
        PaymentMethod method,
        /** Required for every non-cash method — validated by the DB CHECK on payment. */
        String reference,
        String idempotencyKey,
        List<LineAllocationRequest> allocations,
        UUID actorUserId
) {

    public record LineAllocationRequest(UUID invoiceLineId, long amountMinor) {
    }
}
