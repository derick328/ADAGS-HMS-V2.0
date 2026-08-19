package com.adags.modules.billing.domain;

import java.util.UUID;

public class InvoiceCancellationRequestAlreadyDecidedException extends RuntimeException {

    private final UUID requestId;

    public InvoiceCancellationRequestAlreadyDecidedException(UUID requestId) {
        super("Cancellation request " + requestId + " has already been decided.");
        this.requestId = requestId;
    }

    public UUID requestId() {
        return requestId;
    }
}
