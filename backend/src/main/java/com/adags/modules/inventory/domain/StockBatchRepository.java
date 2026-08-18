package com.adags.modules.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBatchRepository {

    StockBatch insert(StockBatch batch);

    Optional<StockBatch> findById(UUID id);

    /**
     * ACTIVE, not-yet-expired batches for an item, earliest expiry first —
     * the FEFO order §5.3 requires. Across every store the item is held
     * in: nothing in this build brief asks the pharmacist to pick a store
     * before a batch, so the store a batch sits in is an implementation
     * detail InventoryFacade.decrementForDispense resolves internally.
     */
    List<StockBatch> findActiveByItemFefo(UUID itemId);
}
