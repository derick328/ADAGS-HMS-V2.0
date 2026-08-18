package com.adags.modules.pharmacy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PlacePrescriptionRequest(
        @NotNull UUID encounterId,
        @NotNull UUID itemId,
        @Positive int quantityPrescribed,
        @NotBlank String dosageInstructions,
        boolean substitutionAllowed
) {
}
