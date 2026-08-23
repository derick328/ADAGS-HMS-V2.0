package com.adags.modules.surgery.domain;

import java.util.UUID;

// The surgeon attests to counts the circulating nurse already entered —
// there is nothing to attest until reconcile has run.
public class ProcedureItemNotReconciledException extends RuntimeException {

    private final UUID procedureItemId;

    public ProcedureItemNotReconciledException(UUID procedureItemId) {
        super("Procedure item has not been reconciled yet: " + procedureItemId);
        this.procedureItemId = procedureItemId;
    }

    public UUID procedureItemId() {
        return procedureItemId;
    }
}
