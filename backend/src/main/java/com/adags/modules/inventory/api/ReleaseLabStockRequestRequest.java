package com.adags.modules.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReleaseLabStockRequestRequest(@NotNull UUID batchId, @Positive @jakarta.validation.constraints.Digits(integer = 14, fraction = 6) double quantity) {
}
