package com.adags.modules.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateLabStockRequestRequest(@NotNull UUID itemId, @Positive int requestedQuantity, @NotNull UUID requestingStoreId) {
}
