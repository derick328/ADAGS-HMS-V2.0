package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.UUID;

// root CLAUDE.md "Concurrency": UNIQUE (bed_id) WHERE released_at IS NULL
// is the actual guard against two nurses assigning the same bed — this
// record is just the row shape; AssignBedService's insert is what can lose
// the race, caught as BedNotAvailableException.
public record BedAssignment(
        UUID id,
        UUID admissionId,
        UUID bedId,
        Instant assignedAt,
        Instant releasedAt,
        UUID assignedBy,
        boolean placedAboveOrderedClass,
        PlacementReason placementReason
) {

    /**
     * 17-ward-nurse.md §1 ⚠2: mirrors
     * bed_assignment_placement_reason_required — placementReason is required
     * exactly when the assigned bed's ward class sits above the admission's
     * ordered class, never otherwise (a reason on a same-class placement
     * would suggest a deviation that didn't happen).
     */
    public static BedAssignment create(
            UUID admissionId, UUID bedId, WardClass bedWardClass, WardClass orderedWardClass,
            PlacementReason placementReason, UUID assignedBy
    ) {
        boolean placedAbove = bedWardClass.isAbove(orderedWardClass);
        if (placedAbove && placementReason == null) {
            throw new MissingPlacementReasonException(bedId);
        }
        if (!placedAbove && placementReason != null) {
            throw new IllegalArgumentException(
                    "placementReason must be null when the bed's ward class does not exceed the ordered class.");
        }
        return new BedAssignment(null, admissionId, bedId, null, null, assignedBy, placedAbove, placementReason);
    }
}
