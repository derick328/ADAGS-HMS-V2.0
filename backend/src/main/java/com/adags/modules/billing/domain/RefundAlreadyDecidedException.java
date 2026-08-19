package com.adags.modules.billing.domain;

import java.util.UUID;

public class RefundAlreadyDecidedException extends RuntimeException {

    private final UUID refundId;

    public RefundAlreadyDecidedException(UUID refundId) {
        super("Refund " + refundId + " has already been decided.");
        this.refundId = refundId;
    }

    public UUID refundId() {
        return refundId;
    }
}
