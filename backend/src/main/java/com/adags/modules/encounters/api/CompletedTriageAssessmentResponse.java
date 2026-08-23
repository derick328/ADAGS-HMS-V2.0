package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriageAssessment;
import com.adags.modules.encounters.domain.TriagePriority;

import java.time.Instant;
import java.util.UUID;

// TriageAssessmentResponse plus patientId — the Completed Assessments list
// (06-triage.md §1) needs to show whose patient this was, which the
// aggregate itself doesn't carry (it only knows its encounterId; patientId
// comes from a same-module join against Encounter in the controller, never
// a second HTTP round trip per row).
public record CompletedTriageAssessmentResponse(
        UUID id,
        UUID encounterId,
        UUID patientId,
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
        String assessedByUsername,
        Instant assessedAt
) {
    public static CompletedTriageAssessmentResponse from(TriageAssessment a, UUID patientId, String assessedByUsername) {
        return new CompletedTriageAssessmentResponse(
                a.id(), a.encounterId(), patientId, a.chiefComplaint(), a.currentSymptoms(), a.allergies(),
                a.comorbidities(), a.modeOfAmbulation(), a.hasPain(), a.painScore(), a.painLocation(), a.fallRisk(),
                a.fallScore(), a.infectionRisk(), a.pregnancyStatus(), a.gestationalWeeks(), a.priority(),
                a.immediateReason(), a.immediateReasonOtherText(), a.notes(), a.supersedesId(), a.assessedBy(),
                assessedByUsername, a.assessedAt());
    }
}
