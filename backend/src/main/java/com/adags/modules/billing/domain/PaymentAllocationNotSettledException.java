package com.adags.modules.billing.domain;

import java.util.UUID;

// A refund may only be requested against an allocation funded by a
// SETTLED payment — a PENDING_CLEARANCE cheque has not actually put money
// in the hospital's hands yet, so there is nothing to give back.
public class PaymentAllocationNotSettledException extends RuntimeException {

    private final UUID paymentAllocationId;

    public PaymentAllocationNotSettledException(UUID paymentAllocationId) {
        super("Payment allocation " + paymentAllocationId + " is not funded by a settled payment — not refundable.");
        this.paymentAllocationId = paymentAllocationId;
    }

    public UUID paymentAllocationId() {
        return paymentAllocationId;
    }
}
