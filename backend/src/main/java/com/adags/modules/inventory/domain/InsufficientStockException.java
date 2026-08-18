package com.adags.modules.inventory.domain;

import java.util.UUID;

// Thrown when applying a StockBalance delta would take quantity below zero
// — the application-layer translation of the stock_balance CHECK
// (quantity >= 0) constraint violation, root CLAUDE.md "Concurrency": two
// concurrent dispenses against the same batch must not both succeed past
// available quantity.
public class InsufficientStockException extends RuntimeException {

    private final UUID batchId;

    public InsufficientStockException(UUID batchId) {
        super("Insufficient stock on batch " + batchId + " for the requested quantity.");
        this.batchId = batchId;
    }

    public UUID batchId() {
        return batchId;
    }
}
