package com.adags.modules.inventory.api;

import jakarta.validation.constraints.NotBlank;

public record RespondToLabStockRequestRequest(@NotBlank String responseNotes) {
}
