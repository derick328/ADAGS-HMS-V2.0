package com.adags.modules.billing.domain;

import java.util.UUID;

// A byte-identical .xlsx was already uploaded (PREVIEWED or APPLIED).
// Points the admin at the existing batch rather than creating a second.
public class DuplicateCatalogFileException extends RuntimeException {

    private final UUID existingBatchId;
    private final String existingStatus;

    public DuplicateCatalogFileException(UUID existingBatchId, String existingStatus) {
        super("This catalogue file has already been uploaded (batch " + existingBatchId + ", status " + existingStatus + ").");
        this.existingBatchId = existingBatchId;
        this.existingStatus = existingStatus;
    }

    public UUID existingBatchId() {
        return existingBatchId;
    }

    public String existingStatus() {
        return existingStatus;
    }
}
