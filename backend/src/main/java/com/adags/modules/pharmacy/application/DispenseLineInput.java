package com.adags.modules.pharmacy.application;

import java.util.UUID;

public record DispenseLineInput(UUID batchId, double quantity, String overrideReason, String requestedUnit) {
    public DispenseLineInput(UUID batchId, double quantity, String overrideReason) {
        this(batchId, quantity, overrideReason, null);
    }
}
