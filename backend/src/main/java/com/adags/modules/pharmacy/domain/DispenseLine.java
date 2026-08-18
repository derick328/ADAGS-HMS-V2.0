package com.adags.modules.pharmacy.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §4.3/§5.3: one row per batch a dispense draws from — 30
// tablets may span batch A (20) and batch B (10). unitCost is copied from
// the batch at dispense time (never recomputed later — "cost of goods for
// a past dispense must not move when a later delivery arrives at a
// different price"). fefoOverrideReason is non-null only when this line's
// batch selection deviates from the FEFO suggestion — validated at the
// whole-dispense level in DispenseService, not here.
public record DispenseLine(
        UUID id,
        UUID dispenseId,
        UUID itemId,
        UUID batchId,
        int quantity,
        Money unitCost,
        String fefoOverrideReason,
        Instant createdAt
) {

    public static DispenseLine create(UUID dispenseId, UUID itemId, UUID batchId, int quantity, Money unitCost,
                                       String fefoOverrideReason) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0.");
        }
        return new DispenseLine(null, dispenseId, itemId, batchId, quantity, unitCost, fefoOverrideReason, null);
    }

    /** Pre-generated id — passed to InventoryFacade.decrementForDispense as the StockMovement's referenceId before this row exists. */
    public DispenseLine withId(UUID id) {
        return new DispenseLine(id, dispenseId, itemId, batchId, quantity, unitCost, fefoOverrideReason, createdAt);
    }
}
