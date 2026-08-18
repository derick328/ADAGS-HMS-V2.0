package com.adags.shared.kernel;

import java.time.LocalDate;
import java.util.UUID;

// What InventoryFacade hands another module (pharmacy) for FEFO batch
// selection — 12-pharmacist.md §5.3. Never a StockBatch (inventory's own
// domain type) across the module boundary.
public record BatchAvailability(UUID batchId, UUID itemId, String batchNumber, LocalDate expiryDate, int quantityAvailable) {
}
