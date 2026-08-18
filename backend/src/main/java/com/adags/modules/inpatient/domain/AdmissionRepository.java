package com.adags.modules.inpatient.domain;

import java.util.Optional;
import java.util.UUID;

public interface AdmissionRepository {

    Optional<Admission> findById(UUID id);

    Optional<Admission> findByEncounterId(UUID encounterId);

    Admission insert(Admission admission);
}
