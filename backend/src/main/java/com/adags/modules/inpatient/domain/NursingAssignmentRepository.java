package com.adags.modules.inpatient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NursingAssignmentRepository {

    List<NursingAssignment> findByAdmissionId(UUID admissionId);

    /** 17-ward-nurse.md §11: "My History" — this nurse's own roster assignments, newest first. */
    List<NursingAssignment> findByNurseId(UUID nurseId);

    /** Whether nurseId holds any roster row for this admission — the DISCHARGE_ORDER scoping check. */
    boolean existsForNurseAndAdmission(UUID admissionId, UUID nurseId);

    Optional<NursingAssignment> findForShift(UUID admissionId, LocalDate date, Shift shift);

    NursingAssignment insert(NursingAssignment assignment);

    /**
     * 07-doctor.md §7: the "who's least loaded right now" number the
     * doctor's Admit-to-ward nurse picker shows. Counts distinct
     * admissions the nurse currently holds care responsibility for —
     * "current" meaning the admission has not left the ward yet
     * (actual_departure_at IS NULL); every nurse this facility employs
     * is included, so a nurse with zero patients still shows up so the
     * doctor can pick them.
     */
    List<NurseWorkload> countActiveAdmissionsPerNurse(UUID facilityId);

    /** Row returned by countActiveAdmissionsPerNurse — nurseId, their app_user.username, and the live patient count. */
    record NurseWorkload(UUID nurseId, String username, int activePatients) {
    }
}
