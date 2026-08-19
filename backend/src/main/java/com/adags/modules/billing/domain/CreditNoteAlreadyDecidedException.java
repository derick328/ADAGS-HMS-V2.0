package com.adags.modules.billing.domain;

import java.util.UUID;

public class CreditNoteAlreadyDecidedException extends RuntimeException {

    private final UUID creditNoteId;

    public CreditNoteAlreadyDecidedException(UUID creditNoteId) {
        super("Credit note " + creditNoteId + " has already been decided.");
        this.creditNoteId = creditNoteId;
    }

    public UUID creditNoteId() {
        return creditNoteId;
    }
}
