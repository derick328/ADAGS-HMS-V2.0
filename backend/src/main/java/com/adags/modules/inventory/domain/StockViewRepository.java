package com.adags.modules.inventory.domain;

import java.util.List;

public interface StockViewRepository {

    /** Every (item, batch, store) with quantity > 0, item name ascending. */
    List<CurrentStockRow> findCurrentStock();

    /** Items whose total on-hand quantity across every store is below their own reorder_level. */
    List<LowStockRow> findLowStock();

    /** ACTIVE batches expiring within {@code windowDays} of today, earliest first. */
    List<ExpiringBatchRow> findExpiringWithin(int windowDays);

    /** SUM(quantity * unit_cost) grouped by store — every currency present is assumed TZS, this codebase's only currency so far. */
    List<StockValuationRow> findStockValuation();
}
