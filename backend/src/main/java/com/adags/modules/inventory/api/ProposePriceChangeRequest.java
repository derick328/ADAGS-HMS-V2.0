package com.adags.modules.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProposePriceChangeRequest(@NotNull UUID itemId, @Min(0) long proposedPriceMinor, @NotBlank String justification) {
}
