package com.adags.modules.surgery.domain;

import java.time.Instant;
import java.util.UUID;

// 04-procedure-bedday-rules.md §2.2: "prepared versus used." A freshly
// prepared row always has quantityUsed/quantityReturned/quantityWasted at
// 0 — reconciliation (item 3) is what fills those in; until then the
// row deliberately does not balance (quantityPrepared == used + returned +
// wasted), because it is mid-procedure, not because anything is wrong.
//
// Charges are built from quantityUsed, not quantityPrepared (item 2's own
// rule) — no charge is posted here at prepare time, only the stock
// decrement (PrepareProcedureItemService, via InventoryFacade.
// decrementForProcedureItem). That decrement is the "consumption is
// discovered, not ordered" moment: the item leaves the store the instant
// it is pulled for the case, regardless of how much of it ends up used.
public record ProcedureItem(
        UUID id,
        UUID surgeryOrderId,
        UUID itemId,
        ProcedurePhase phase,
        int quantityPrepared,
        int quantityUsed,
        int quantityReturned,
        int quantityWasted,
        String wastageReason,
        UUID batchId,
        UUID preparedBy,
        Instant preparedAt,
        UUID reconciledBy,
        UUID attestedBy,
        Instant reconciledAt
) {

    public static ProcedureItem prepare(
            UUID surgeryOrderId, UUID itemId, ProcedurePhase phase, int quantityPrepared, UUID batchId, UUID preparedBy
    ) {
        return new ProcedureItem(null, surgeryOrderId, itemId, phase, quantityPrepared, 0, 0, 0, null, batchId,
                preparedBy, null, null, null, null);
    }

    /**
     * 04-procedure-bedday-rules.md §2.1: "Intra-op items cannot be gated
     * because nothing knows they will be used until they have been. The
     * system infers DEFERRED from phase = INTRA_OP; it is not a
     * permission anyone holds." No stored flag — this derives it.
     */
    public boolean isDeferred() {
        return phase == ProcedurePhase.INTRA_OP;
    }

    // Same pre-generated-id pattern as SurgeryOrder.withId: the stock
    // decrement (InventoryFacade.decrementForProcedureItem) needs this
    // row's id to tag the StockMovement before the row itself exists.
    public ProcedureItem withId(UUID id) {
        return new ProcedureItem(id, surgeryOrderId, itemId, phase, quantityPrepared, quantityUsed,
                quantityReturned, quantityWasted, wastageReason, batchId, preparedBy, preparedAt, reconciledBy,
                attestedBy, reconciledAt);
    }
}
