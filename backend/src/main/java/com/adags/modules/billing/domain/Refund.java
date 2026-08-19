package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// Phase 7 item 1: "Money going back out. Request -> ACCOUNTANT approves.
// Never a receptionist action. Second signature above the threshold. A
// refund can only exist against a settled payment allocation, so it
// references the Payment, not just the invoice" — modelled here against
// the specific PaymentAllocation (not the raw Payment), since one
// payment's allocations can span more than one invoice (03-billing-
// addendum.md §1) and a refund must be traceable to the exact line it
// reverses.
public record Refund(
        UUID id,
        UUID paymentAllocationId,
        Money amount,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        boolean requiresSecondSignature,
        RefundStatus status,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote
) {

    public static Refund request(UUID paymentAllocationId, Money amount, String reason, UUID requestedBy,
                                  boolean requiresSecondSignature) {
        if (amount.amountMinor() <= 0) {
            throw new IllegalArgumentException("Refund amount must be > 0.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A refund reason is required.");
        }
        return new Refund(null, paymentAllocationId, amount, reason, requestedBy, null,
                requiresSecondSignature, RefundStatus.PENDING, null, null, null);
    }
}
