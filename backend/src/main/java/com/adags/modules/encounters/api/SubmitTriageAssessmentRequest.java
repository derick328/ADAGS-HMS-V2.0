package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriagePriority;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Mirrors SubmitTriageAssessmentCommand's full field set (06-triage.md §2)
// minus what the server supplies itself: encounterId (path variable),
// actorUserId (Actor), occurredAt (Clock). Deliberately unvalidated beyond
// @NotNull on the two fields the service checks before anything else
// (encounter lookup, MissingReferralException) — every other field's
// requiredness is TriageAssessment.create()'s job (InvalidTriageAssessmentException),
// one source of truth for the conditional rules rather than duplicated here.
public record SubmitTriageAssessmentRequest(
        @NotNull Integer expectedEncounterVersion,
        @NotNull UUID assignedProviderId,

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

        double temperatureC,
        double systolicMmHg,
        double diastolicMmHg,
        double pulseBpm,
        double respiratoryRate,
        double spo2Pct,
        double weightKg,
        double heightCm,
        String avpu,
        Double bloodGlucoseMmol
) {
}
