package com.adags.modules.laboratory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// §10: "a test approved without parameters generates an empty result
// form" — parameters may legitimately be empty (a purely manual/qualitative
// test), so no @NotEmpty here.
public record ProposeLabItemRequest(
        @NotBlank String proposedName,
        @NotBlank String proposedClassification,
        @NotNull @Min(0) Long proposedPriceMinor,
        String notes,
        @NotBlank String specimenType,
        @NotBlank String containerType,
        @NotNull @Positive Integer expectedTatHours,
        @NotBlank String justification,
        @Valid List<ProposedParameterRequest> parameters
) {
    public record ProposedParameterRequest(@NotBlank String name, String unit, String method) {
    }
}
