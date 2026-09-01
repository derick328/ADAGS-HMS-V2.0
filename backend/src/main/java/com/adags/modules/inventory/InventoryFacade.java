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

    /**
     * V72 catalogue-import bootstrap: create an inventory.item row for a
     * PHARMACY service_item that has none yet, so a doctor can select it
     * in the prescription typeahead immediately after admin upload. The
     * item lands classified as PRESCRIPTION_ONLY and active — the safe
     * default per V71's Reg 16(3) reasoning; a pharmacist-in-charge can
     * refine schedule/form/strength via /api/items/{id}/classify later.
     * Idempotent: returns the existing item id when the code is already
     * taken, so a re-import of the same catalogue file never duplicates.
     */
    UUID ensureCatalogueItem(String code, String name, String classification, UUID actorUserId);

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

    /**
     * Phase 8 item 2 (surgery module): same mechanics as
     * {@link #decrementForDispense} — decrements the materialised balance
     * (throwing {@link com.adags.modules.inventory.domain.InsufficientStockException}
     * if it would go negative), checks {@link com.adags.modules.inventory.domain.Item#assertDispensable()}
     * so a CONTROLLED item is blocked here the same as every other
     * consuming path (root CLAUDE.md "Safety": "Enforce in the repository
     * layer so new endpoints inherit it"), and checks batch expiry/status
     * — but tags the StockMovement {@code PROCEDURE_ITEM} rather than
     * {@code DISPENSE_LINE}, so the ledger traces a suture pulled for
     * surgery back to its ProcedureItem row, not a pharmacy dispense.
     * {@code referenceId} is the caller's pre-generated ProcedureItem id,
     * same pre-generated-id pattern as {@code decrementForDispense}.
     */
    Money decrementForProcedureItem(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId);

    /**
     * Phase 8 item 3 (surgery module): "Returned items write a positive
     * StockMovement back to the source batch, or they are lost from the
     * ledger" (04-procedure-bedday-rules.md §2.2). Credits the
     * materialised balance and records a RETURN StockMovement tagged
     * {@code PROCEDURE_ITEM_RETURN}, against the same batch the item was
     * originally prepared from — no dispensability/expiry check, since
     * crediting stock back is never itself the blocked action (root
     * CLAUDE.md "Safety" blocks CONTROLLED from consumption, not from
     * being accounted for).
     */
    void incrementForReturn(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId);
}
