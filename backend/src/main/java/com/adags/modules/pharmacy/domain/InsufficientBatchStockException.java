package com.adags.modules.pharmacy.domain;

import java.util.UUID;

// Distinct from com.adags.modules.inventory.domain.InsufficientStockException
// (which is about one batch's balance) — this is "the sum of every active
// batch for this item can't cover the requested quantity," raised by
// FefoAllocator before any batch is even chosen.
public class InsufficientBatchStockException extends RuntimeException {

    private final UUID itemId;

    public InsufficientBatchStockException(UUID itemId, int requested, int available) {
        super("Item " + itemId + ": requested " + requested + " but only " + available + " available across active batches.");
        this.itemId = itemId;
    }

    public UUID itemId() {
        return itemId;
    }
}
