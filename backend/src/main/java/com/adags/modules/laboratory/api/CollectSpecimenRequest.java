package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.SpecimenQuality;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CollectSpecimenRequest(
        @NotNull UUID labOrderId,
        @NotNull SpecimenQuality quality,
        String qualityNote
) {
}
