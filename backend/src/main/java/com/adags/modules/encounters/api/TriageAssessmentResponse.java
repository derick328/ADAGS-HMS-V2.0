package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriageAssessment;
import com.adags.modules.encounters.domain.TriagePriority;

import java.time.Instant;
import java.util.UUID;

public record TriageAssessmentResponse(
        UUID id,
        UUID encounterId,
        String chiefComplaint,
        String currentSymptoms,
        String allergies,
        String comorbidities,
        ModeOfAmbulation modeOfAmbulation,
        boolean hasPain,
        Integer painScore,
        String painLocation,
        boolean fallRisk,
        Integer fallScore,
        boolean infectionRisk,
        PregnancyStatus pregnancyStatus,
        Integer gestationalWeeks,
        TriagePriority priority,
        ImmediateReason immediateReason,
        String immediateReasonOtherText,
        String notes,
        UUID supersedesId,
        UUID assessedBy,
        Instant assessedAt
) {
    public static TriageAssessmentResponse from(TriageAssessment a) {
        return new TriageAssessmentResponse(
                a.id(), a.encounterId(), a.chiefComplaint(), a.currentSymptoms(), a.allergies(), a.comorbidities(),
                a.modeOfAmbulation(), a.hasPain(), a.painScore(), a.painLocation(), a.fallRisk(), a.fallScore(),
                a.infectionRisk(), a.pregnancyStatus(), a.gestationalWeeks(), a.priority(), a.immediateReason(),
                a.immediateReasonOtherText(), a.notes(), a.supersedesId(), a.assessedBy(), a.assessedAt());
    }
}
