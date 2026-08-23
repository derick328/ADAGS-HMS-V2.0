package com.adags.modules.surgery.domain;

import java.util.UUID;

public class ProcedureItemAlreadyAttestedException extends RuntimeException {

    private final UUID procedureItemId;

    public ProcedureItemAlreadyAttestedException(UUID procedureItemId) {
        super("Procedure item already attested: " + procedureItemId);
        this.procedureItemId = procedureItemId;
    }

    public UUID procedureItemId() {
        return procedureItemId;
    }
}
