package com.adags.modules.inventory.domain;

import java.util.UUID;

// Materialised, derived from StockMovement — never written to directly
// except by applying a movement's delta inside the same transaction as the
// movement insert (root CLAUDE.md "Data" and "Concurrency").
public record StockBalance(UUID itemId, UUID batchId, UUID storeId, int quantity) {
}
