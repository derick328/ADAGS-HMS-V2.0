package com.adags.modules.billing.domain;

import java.util.UUID;

public class InvoiceWriteOffAlreadyDecidedException extends RuntimeException {

    private final UUID writeOffId;

    public InvoiceWriteOffAlreadyDecidedException(UUID writeOffId) {
        super("Write-off " + writeOffId + " has already been decided.");
        this.writeOffId = writeOffId;
    }

    public UUID writeOffId() {
        return writeOffId;
    }
}
