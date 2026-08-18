package com.adags.modules.pharmacy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProposeSubstitutionRequest(@NotNull UUID proposedItemId, @NotBlank String reason) {
}
