package com.adags.modules.inventory;

import com.adags.shared.kernel.BatchAvailability;
import com.adags.shared.kernel.ItemSummary;
import com.adags.shared.kernel.Money;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Every parameter/return type here is a UUID, a String, a
// primitive, or shared.kernel vocabulary — never an inventory.domain type
// — so a caller (pharmacy) never needs to import across the module
// boundary. Exceptions thrown are unchecked inventory.domain types
// (ControlledItemBlockedException, InsufficientStockException,
// BatchExpiredException); a caller that doesn't catch them — the normal
// case, letting them propagate to GlobalExceptionHandler — never needs to
// import them either, same treatment BillingFacade documents for its own.
public interface InventoryFacade {

    boolean itemExists(UUID itemId);

    /** False for a missing item too — a caller checking dispensability doesn't need a separate not-found branch. */
    boolean isDispensable(UUID itemId);

    Optional<ItemSummary> findItemSummary(UUID itemId);

    /** ACTIVE, not-yet-expired batches for the item, earliest expiry first — 12-pharmacist.md §5.3's FEFO order. */
    List<BatchAvailability> findFefoBatches(UUID itemId);

    /** Any batch by id, regardless of status or expiry — 12-pharmacist.md §4.6's dispensing label needs the batch number and expiry of what was actually dispensed, even if since expired. */
    Optional<BatchAvailability> findBatch(UUID batchId);

    /**
     * Dispenses {@code quantity} of {@code itemId} from {@code batchId}:
     * decrements the materialised balance ({@link com.adags.modules.inventory.domain.InsufficientStockException}
     * if it would go negative), records a DISPENSE StockMovement tagged
     * with {@code referenceId} (the caller's pre-generated DispenseLine
     * id, same pre-generated-id pattern {@code LabOrder.withId} uses so
     * the movement can point at a row that doesn't exist yet in this
     * transaction), and returns the batch's unit cost to snapshot on the
     * caller's DispenseLine (12-pharmacist.md §5.3: "cost of goods for a
     * past dispense must not move when a later delivery arrives at a
     * different price"). Throws {@link com.adags.modules.inventory.domain.BatchExpiredException}
     * if the batch is past its expiry_date or not ACTIVE (12-pharmacist.md
     * §6.3: hard-blocked, not flagged), or
     * {@link com.adags.modules.inventory.domain.ControlledItemBlockedException}
     * if the item's regulatory_schedule has changed to CONTROLLED since
     * prescribing (evaluated live, same discipline as the billing gate).
     */
    Money decrementForDispense(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId);
}
