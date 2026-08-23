package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.TriagePriority;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TriageAssessmentDraftResponse(
        UUID encounterId,
        UUID authorId,
        TriagePriority priority,
        ModeOfAmbulation modeOfAmbulation,
        Map<String, Object> formState,
        Instant updatedAt
) {
}
