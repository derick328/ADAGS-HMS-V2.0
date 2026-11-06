package com.adags.modules.pharmacy.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record DispenseLineRequest(@NotNull UUID batchId, @Positive int quantity, String overrideReason, String requestedUnit) {
}
