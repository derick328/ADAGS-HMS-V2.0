package com.adags.modules.inpatient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NursingAssignmentRepository {

    List<NursingAssignment> findByAdmissionId(UUID admissionId);

    /** Whether nurseId holds any roster row for this admission — the DISCHARGE_ORDER scoping check. */
    boolean existsForNurseAndAdmission(UUID admissionId, UUID nurseId);

    Optional<NursingAssignment> findForShift(UUID admissionId, LocalDate date, Shift shift);

    NursingAssignment insert(NursingAssignment assignment);
}
