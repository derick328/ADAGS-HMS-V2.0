package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.PlacementReason;

import java.time.Instant;
import java.util.UUID;

public record BedAssignmentResponse(
        UUID id,
        UUID admissionId,
        UUID bedId,
        Instant assignedAt,
        Instant releasedAt,
        UUID assignedBy,
        boolean placedAboveOrderedClass,
        PlacementReason placementReason
) {
    public static BedAssignmentResponse from(BedAssignment assignment) {
        return new BedAssignmentResponse(assignment.id(), assignment.admissionId(), assignment.bedId(),
                assignment.assignedAt(), assignment.releasedAt(), assignment.assignedBy(),
                assignment.placedAboveOrderedClass(), assignment.placementReason());
    }
}
