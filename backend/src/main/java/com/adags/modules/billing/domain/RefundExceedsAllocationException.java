package com.adags.modules.billing.domain;

import java.util.UUID;

// Thrown by PaymentAllocationRepository.recordRefund when amount exceeds
// what remains refundable on the allocation (amount_minor - refunded_minor)
// at the moment of the update — including a concurrent refund having
// already consumed the balance this one was validated against.
public class RefundExceedsAllocationException extends RuntimeException {

    private final UUID paymentAllocationId;

    public RefundExceedsAllocationException(UUID paymentAllocationId) {
        super("Refund against payment allocation " + paymentAllocationId
                + " could not be applied — the amount exceeds what remains refundable on it.");
        this.paymentAllocationId = paymentAllocationId;
    }

    public UUID paymentAllocationId() {
        return paymentAllocationId;
    }
}
