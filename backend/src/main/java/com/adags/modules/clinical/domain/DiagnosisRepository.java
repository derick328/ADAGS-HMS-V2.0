package com.adags.modules.clinical.domain;

import java.util.List;
import java.util.UUID;

public interface DiagnosisRepository {

    Diagnosis insert(Diagnosis diagnosis);

    List<Diagnosis> findByEncounterId(UUID encounterId);

    /** §4.1: "Exactly one may be primary" — used by finalize to check provisional_diagnosis is satisfied. */
    boolean existsPrimaryForEncounter(UUID encounterId);
}
