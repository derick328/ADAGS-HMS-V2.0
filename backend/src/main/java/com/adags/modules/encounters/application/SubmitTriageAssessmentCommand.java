package com.adags.modules.encounters.application;

import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriagePriority;

import java.time.Instant;
import java.util.UUID;

// Raw, unvalidated input for SubmitTriageAssessmentService — mirrors
// 06-triage.md §2's full field set plus the submission-transaction inputs
// (§5's mandatory referral, §6's actor/version). Validation and conditional
// nulling happen downstream in TriageAssessment.create(); this command
// carries exactly what the client submitted.
public record SubmitTriageAssessmentCommand(
        UUID encounterId,
        int expectedEncounterVersion,
        UUID assignedProviderId,

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
        Double bloodGlucoseMmol,

        UUID actorUserId,
        Instant occurredAt
) {
}
