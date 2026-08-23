package com.adags.modules.encounters.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TriageAssessmentRepository {

    TriageAssessment insert(TriageAssessment triageAssessment);

    Optional<TriageAssessment> findById(UUID id);

    /** Newest first — the current assessment is the one with no other row's supersedes_id pointing past it. */
    List<TriageAssessment> findByEncounterId(UUID encounterId);

    /**
     * 06-triage.md §1's "Completed Assessments" screen — current (never
     * superseded) assessments in [assessedFrom, assessedTo), newest first.
     * {@code assessedBy} narrows to one nurse's own work; null returns
     * every author's — the same query backs both "mine today" and "the
     * whole shift" (§7.12 leaves that scope split open; this resolves it
     * as one endpoint, two callers).
     */
    List<TriageAssessment> search(UUID assessedBy, Instant assessedFrom, Instant assessedTo);
}
