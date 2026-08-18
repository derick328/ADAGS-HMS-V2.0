package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.PlacementReason;

import java.util.UUID;

public record AssignBedCommand(
        UUID admissionId,
        UUID bedId,
        PlacementReason placementReason,
        UUID assignedBy
) {
}
