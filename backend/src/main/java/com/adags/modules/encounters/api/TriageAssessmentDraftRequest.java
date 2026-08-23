package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.TriagePriority;

import java.util.Map;

// 06-triage.md §1/§3 autosave body. formState is deliberately untyped
// (opaque JSONB, V60) — every other field on the eventual Screen 2 form,
// saved as-is for resume. priority/modeOfAmbulation are pulled out
// because the queue projection reads them without parsing formState.
public record TriageAssessmentDraftRequest(
        TriagePriority priority,
        ModeOfAmbulation modeOfAmbulation,
        Map<String, Object> formState
) {
}
