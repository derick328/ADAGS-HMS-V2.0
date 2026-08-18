package com.adags.modules.inventory.domain;

import java.util.UUID;

// 12-pharmacist.md §6.1: "low-stock alerts below reorder_level" — total
// on-hand quantity for an item across every store, summed against a
// single reorder_level (the item's own, not per-store).
public record LowStockRow(UUID itemId, String itemCode, String itemName, int totalQuantity, int reorderLevel) {
}
