package com.adags.modules.inpatient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionRepository {

    Optional<Admission> findById(UUID id);

    Optional<Admission> findByEncounterId(UUID encounterId);

    Admission insert(Admission admission);

    /** Admissions with a currently active (unreleased) BedAssignment — the nightly accrual job's candidate set. */
    List<Admission> findAllWithActiveBedAssignment();
}
