package com.adags.modules.encounters.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TriageAssessmentRepository {

    TriageAssessment insert(TriageAssessment triageAssessment);

    Optional<TriageAssessment> findById(UUID id);

    /** Newest first — the current assessment is the one with no other row's supersedes_id pointing past it. */
    List<TriageAssessment> findByEncounterId(UUID encounterId);
}
