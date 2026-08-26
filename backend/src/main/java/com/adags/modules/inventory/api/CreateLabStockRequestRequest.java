package com.adags.modules.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateLabStockRequestRequest(
        @NotNull UUID itemId,
        @Positive int requestedQuantity,
        @NotNull UUID requestingStoreId,
        @Size(max = 500) String requestNotes
) {
}
