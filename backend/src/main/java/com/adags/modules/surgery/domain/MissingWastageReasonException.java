package com.adags.modules.surgery.domain;

import java.util.UUID;

// Same idiom as MissingPlacementReasonException/MissingCloseReasonException
// etc — the friendly application-layer rejection before the DB CHECK
// (quantity_wasted = 0 OR wastage_reason IS NOT NULL, V51) is ever reached.
public class MissingWastageReasonException extends RuntimeException {

    private final UUID procedureItemId;

    public MissingWastageReasonException(UUID procedureItemId) {
        super("Wastage reason is required when quantity_wasted > 0: procedure item " + procedureItemId);
        this.procedureItemId = procedureItemId;
    }

    public UUID procedureItemId() {
        return procedureItemId;
    }
}
