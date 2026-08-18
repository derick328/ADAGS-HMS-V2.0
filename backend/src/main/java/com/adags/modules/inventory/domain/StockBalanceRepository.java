package com.adags.modules.inventory.domain;

import java.util.UUID;

public interface StockBalanceRepository {

    /**
     * Upserts the (item, batch, store) balance row by {@code delta} and
     * returns the resulting quantity. The underlying SQL is an
     * {@code INSERT ... ON CONFLICT DO UPDATE}, the same row-locking
     * mechanism {@code JdbcGaplessNumberAllocator} uses — two concurrent
     * callers for the same batch serialise on this row. A delta that would
     * take quantity below zero is rejected by the {@code quantity >= 0}
     * database CHECK and surfaces here as {@link InsufficientStockException}.
     */
    int applyDelta(UUID itemId, UUID batchId, UUID storeId, int delta);

    /** 0 if the batch has never had a balance row (never received into any store) — never absent, never negative. */
    int quantityOnHand(UUID batchId);
}
