package com.adags.modules.inventory.domain;

import com.adags.shared.kernel.Money;

import java.util.UUID;

// 12-pharmacist.md §6.1: "stock valuation (SUM(quantity x unit_cost) by
// store) — the accountant needs valuation and it is otherwise
// uncomputable." unit_cost lives on the batch, never the item, so this can
// only be computed by joining stock_balance to stock_batch — there is no
// shortcut through a single price field.
public record StockValuationRow(UUID storeId, String storeName, Money totalValue) {
}
