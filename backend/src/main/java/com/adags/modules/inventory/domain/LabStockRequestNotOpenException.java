package com.adags.modules.inventory.domain;

import java.util.UUID;

// Thrown releasing, rejecting, or closing a request that is already
// RELEASED, REJECTED, or CLOSED — nothing further can happen to it.
public class LabStockRequestNotOpenException extends RuntimeException {

    private final UUID requestId;

    public LabStockRequestNotOpenException(UUID requestId, LabStockRequestStatus status) {
        super("Lab stock request " + requestId + " is " + status + " — not open for this action.");
        this.requestId = requestId;
    }

    public UUID requestId() {
        return requestId;
    }
}
