package com.adags.modules.surgery.domain;

import java.util.UUID;

// 04-procedure-bedday-rules.md §2.3: "quantity_prepared == quantity_used +
// quantity_returned + quantity_wasted." A nurse's reconciliation entry
// that doesn't add up is rejected here, before it's ever written —
// "without this step, prepared-but-unaccounted items sit forever and stock
// drifts silently."
public class ProcedureItemNotBalancedException extends RuntimeException {

    private final UUID procedureItemId;

    public ProcedureItemNotBalancedException(UUID procedureItemId, int prepared, int used, int returned, int wasted) {
        super("Procedure item " + procedureItemId + " does not balance: prepared=" + prepared
                + " but used(" + used + ") + returned(" + returned + ") + wasted(" + wasted + ") = "
                + (used + returned + wasted));
        this.procedureItemId = procedureItemId;
    }

    public UUID procedureItemId() {
        return procedureItemId;
    }
}
