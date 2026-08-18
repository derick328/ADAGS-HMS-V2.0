package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.PlacementReason;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignBedRequest(
        @NotNull UUID bedId,
        /** Required only when the bed's ward class is above the admission's ordered class. */
        PlacementReason placementReason
) {
}
