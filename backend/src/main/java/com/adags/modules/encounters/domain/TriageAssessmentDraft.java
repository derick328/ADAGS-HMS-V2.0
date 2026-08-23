package com.adags.modules.encounters.domain;

import java.time.Instant;
import java.util.UUID;

// 06-triage.md §1/§3 autosave. Not the clinical record (that's
// TriageAssessment, V11) — freely overwritten scratch state keyed to
// (encounter_id, author_id), superseded entirely once submission creates
// the real, immutable row. formStateJson is opaque here by design (see
// V60's migration comment); priority/modeOfAmbulation are pulled out
// because the queue projection needs them without parsing it.
public record TriageAssessmentDraft(
        UUID encounterId,
        UUID authorId,
        TriagePriority priority,
        ModeOfAmbulation modeOfAmbulation,
        String formStateJson,
        Instant updatedAt
) {
}
