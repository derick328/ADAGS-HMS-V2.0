package com.adags.modules.surgery.domain;

import java.util.UUID;

// root CLAUDE.md task text: "CHECK (reconciled_by != attested_by) would be
// ideal but both may be nullable during the procedure; enforce at the
// service layer with the same pattern as other second-person checks" —
// same idiom as ExpenseSelfApprovalNotAllowedException, deliberately with
// no DB CHECK backing it (see V51's comment).
public class ProcedureItemSelfReconciliationNotAllowedException extends RuntimeException {

    private final UUID procedureItemId;

    public ProcedureItemSelfReconciliationNotAllowedException(UUID procedureItemId) {
        super("The circulating nurse and attesting surgeon must be different people: procedure item "
                + procedureItemId);
        this.procedureItemId = procedureItemId;
    }

    public UUID procedureItemId() {
        return procedureItemId;
    }
}
