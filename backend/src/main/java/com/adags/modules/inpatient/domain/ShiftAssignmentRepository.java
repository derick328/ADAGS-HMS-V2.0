package com.adags.modules.inpatient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftAssignmentRepository {

    ShiftAssignment insert(ShiftAssignment assignment);

    Optional<ShiftAssignment> findById(UUID id);

    /**
     * 19-work-shifts-roster.md §4.2: "The roster is what identifies the
     * incoming nurse." What ShiftHandover's incoming-nurse resolution
     * reads — every WARD_NURSE assignment for this ward and date, in
     * assigned_at order, so the caller can pick the next one after the
     * outgoing shift.
     */
    List<ShiftAssignment> findByWardIdAndShiftDate(UUID wardId, LocalDate shiftDate);

    List<ShiftAssignment> findByUserIdAndShiftDate(UUID userId, LocalDate shiftDate);
}
