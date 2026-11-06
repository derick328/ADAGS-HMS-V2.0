package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlaceLabOrderRequest(
        @NotNull UUID encounterId,
        @NotBlank String labTestCode,
        String clinicalIndication,
        String specialInstructions,
        @NotNull Urgency urgency
) {
}
