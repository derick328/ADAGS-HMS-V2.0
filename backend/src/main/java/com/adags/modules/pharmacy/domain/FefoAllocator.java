package com.adags.modules.pharmacy.domain;

import com.adags.shared.kernel.BatchAvailability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 12-pharmacist.md §5.3: "The system proposes batches in first-expiry-
// first-out order and fills the quantity automatically" — a pure function
// over the (already expiry-ordered) batches InventoryFacade.findFefoBatches
// returns, no I/O. Greedy fill: take everything from the earliest-expiring
// batch before touching the next.
public final class FefoAllocator {

    private FefoAllocator() {
    }

    public static List<BatchAllocation> allocate(UUID itemId, List<BatchAvailability> batchesByExpiry, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0.");
        }
        List<BatchAllocation> result = new ArrayList<>();
        int remaining = quantity;
        for (BatchAvailability batch : batchesByExpiry) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, batch.quantityAvailable());
            if (take > 0) {
                result.add(new BatchAllocation(batch.batchId(), take));
                remaining -= take;
            }
        }
        if (remaining > 0) {
            throw new InsufficientBatchStockException(itemId, quantity, quantity - remaining);
        }
        return result;
    }
}
