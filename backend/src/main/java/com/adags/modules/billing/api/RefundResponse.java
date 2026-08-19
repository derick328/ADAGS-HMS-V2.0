package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.Refund;
import com.adags.modules.billing.domain.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentAllocationId,
        long amountMinor,
        String currencyCode,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        boolean requiresSecondSignature,
        RefundStatus status,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote
) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(refund.id(), refund.paymentAllocationId(), refund.amount().amountMinor(),
                refund.amount().currencyCode(), refund.reason(), refund.requestedBy(), refund.requestedAt(),
                refund.requiresSecondSignature(), refund.status(), refund.decidedBy(), refund.decidedAt(),
                refund.decisionNote());
    }
}
