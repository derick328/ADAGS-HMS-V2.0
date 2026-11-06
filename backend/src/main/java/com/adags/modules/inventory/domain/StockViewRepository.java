package com.adags.modules.inventory.domain;

import java.util.List;

public interface StockViewRepository {

    /** Every (item, batch, store) with quantity > 0, item name ascending. */
    List<CurrentStockRow> findCurrentStock();
    List<CurrentStockRow> findCurrentStock(List<java.util.UUID> storeIds);

    /** Items whose total on-hand quantity across every store is below their own reorder_level. */
    List<LowStockRow> findLowStock();
    List<LowStockRow> findLowStock(List<java.util.UUID> storeIds);

    /** ACTIVE batches expiring within {@code windowDays} of today, earliest first. */
    List<ExpiringBatchRow> findExpiringWithin(int windowDays);
    List<ExpiringBatchRow> findExpiringWithin(int windowDays, List<java.util.UUID> storeIds);

    /** SUM(quantity * unit_cost) grouped by store — every currency present is assumed TZS, this codebase's only currency so far. */
    List<StockValuationRow> findStockValuation();
    List<StockValuationRow> findStockValuation(List<java.util.UUID> storeIds);
}
