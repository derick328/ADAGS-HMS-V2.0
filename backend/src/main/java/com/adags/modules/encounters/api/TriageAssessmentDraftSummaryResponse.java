package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.TriagePriority;

import java.time.Instant;
import java.util.UUID;

// 06-triage.md §3's queue projection: "Being assessed by N. Kimaro" as an
// advisory line, plus what a live draft can offer the queue row that a
// bare AWAITING_TRIAGE encounter can't yet — the priority the nurse is
// leaning toward (for the live re-sort) and how the patient arrived, once
// the assessment has captured it. Deliberately excludes the rest of
// formState — the queue shows who's assessing whom, not the in-progress
// form content of another nurse's draft.
public record TriageAssessmentDraftSummaryResponse(
        UUID encounterId,
        UUID authorId,
        String authorUsername,
        TriagePriority priority,
        ModeOfAmbulation modeOfAmbulation,
        Instant updatedAt
) {
}
