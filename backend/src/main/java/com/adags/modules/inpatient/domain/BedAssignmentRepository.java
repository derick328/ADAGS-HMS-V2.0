package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BedAssignmentRepository {

    /** The active (released_at IS NULL) assignment for this admission, if any. */
    Optional<BedAssignment> findActiveByAdmissionId(UUID admissionId);

    /** Full history for this admission, newest first. */
    List<BedAssignment> findByAdmissionId(UUID admissionId);

    /**
     * Throws BedNotAvailableException if the bed already has an active
     * assignment — idx_bed_assignment_one_active_per_bed caught the race.
     */
    BedAssignment insert(BedAssignment assignment);

    void release(UUID bedAssignmentId, Instant releasedAt);
}
