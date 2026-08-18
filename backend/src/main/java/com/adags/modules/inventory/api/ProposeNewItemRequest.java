package com.adags.modules.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProposeNewItemRequest(
        @NotBlank String proposedName,
        @NotBlank String proposedClassification,
        @NotBlank String proposedForm,
        @NotBlank String proposedUnit,
        @Min(0) long proposedPriceMinor,
        String justification
) {
}
