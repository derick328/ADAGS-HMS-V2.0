package com.adags.modules.surgery.domain;

import java.util.UUID;

// A ProcedureItem is reconciled exactly once — a second reconcile call
// would double the InventoryFacade return-stock-movement for the same
// physical return. Same "already decided" idiom as
// InvoiceWriteOffAlreadyDecidedException / CreditNoteAlreadyDecidedException.
public class ProcedureItemAlreadyReconciledException extends RuntimeException {

    private final UUID procedureItemId;

    public ProcedureItemAlreadyReconciledException(UUID procedureItemId) {
        super("Procedure item already reconciled: " + procedureItemId);
        this.procedureItemId = procedureItemId;
    }

    public UUID procedureItemId() {
        return procedureItemId;
    }
}
