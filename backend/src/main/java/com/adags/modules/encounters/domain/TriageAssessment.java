package com.adags.modules.encounters.domain;

import java.time.Instant;
import java.util.UUID;

// 06-triage.md §2. Construction only goes through the static factory below
// — the canonical (record) constructor is public because Java records
// require that, but every caller in this codebase must use create(), which
// is where the conditional-nulling and requiredness rules the spec demands
// actually live: "the UI hides the fields, the server discards any
// submitted values when has_pain is false, and both become required when
// it is true" (§2.5), and the identical rule for fall_risk/fall_score
// (§2.6). id/assessedAt are null on an unpersisted instance — the
// repository fills them in on insert (V11__triage_assessment.sql: both are
// DB-generated).
public record TriageAssessment(
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

    /**
     * 06-triage.md §6: VitalSigns.context_ref_id (item 2) must point at this
     * assessment's id, but that id is normally DB-generated on insert —
     * unresolvable at the point VitalSigns is inserted first (step 1, before
     * step 2's TriageAssessment insert). The submission transaction
     * generates the id client-side and stamps it here before either insert,
     * so both statements can use the same id in the spec's literal order.
     */
    public TriageAssessment withId(UUID id) {
        return new TriageAssessment(id, encounterId, chiefComplaint, currentSymptoms, allergies, comorbidities,
                modeOfAmbulation, hasPain, painScore, painLocation, fallRisk, fallScore, infectionRisk,
                pregnancyStatus, gestationalWeeks, priority, immediateReason, immediateReasonOtherText,
                notes, supersedesId, assessedBy, assessedAt);
    }

    public static TriageAssessment create(
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
            boolean pregnancyPromptApplicable,
            PregnancyStatus pregnancyStatus,
            Integer gestationalWeeks,
            TriagePriority priority,
            ImmediateReason immediateReason,
            String immediateReasonOtherText,
            String notes,
            UUID supersedesId,
            UUID assessedBy
    ) {
        requireNonNull(encounterId, "encounterId");
        if (chiefComplaint != null) {
            requireInRange(chiefComplaint, "chief_complaint", 3, 500);
        }
        if (currentSymptoms != null) requireNonBlank(currentSymptoms, "current_symptoms");
        if (allergies != null) requireNonBlank(allergies, "allergies");
        if (comorbidities != null) requireNonBlank(comorbidities, "comorbidities");
        requireNonNull(priority, "priority");
        requireNonNull(assessedBy, "assessedBy");
        if (notes != null && notes.length() > 2000) {
            throw new InvalidTriageAssessmentException("notes must be at most 2000 characters.");
        }

        Integer effectivePainScore = null;
        String effectivePainLocation = null;
        if (hasPain) {
            if (painScore != null && (painScore < 0 || painScore > 10)) {
                throw new InvalidTriageAssessmentException("pain_score must be between 0 and 10.");
            }
            if (isBlank(painLocation)) {
                throw new InvalidTriageAssessmentException("pain_location is required when has_pain is true.");
            }
            effectivePainScore = painScore;
            effectivePainLocation = painLocation;
        }

        Integer effectiveFallScore = null;
        if (fallRisk) {
            effectiveFallScore = fallScore;
        }

        PregnancyStatus effectivePregnancyStatus = null;
        Integer effectiveGestationalWeeks = null;
        if (pregnancyPromptApplicable) {
            if (pregnancyStatus == null) {
                throw new InvalidTriageAssessmentException(
                        "pregnancy_status is required for a patient within the pregnancy-prompt age bounds.");
            }
            effectivePregnancyStatus = pregnancyStatus;
            effectiveGestationalWeeks = gestationalWeeks;
        }

        ImmediateReason effectiveImmediateReason = null;
        String effectiveImmediateReasonOtherText = null;
        if (priority == TriagePriority.IMMEDIATE) {
            if (immediateReason == null) {
                throw new InvalidTriageAssessmentException("immediate_reason is required when priority is IMMEDIATE.");
            }
            effectiveImmediateReason = immediateReason;
            if (immediateReason == ImmediateReason.OTHER) {
                if (isBlank(immediateReasonOtherText)) {
                    throw new InvalidTriageAssessmentException(
                            "immediate_reason_other_text is required when immediate_reason is OTHER.");
                }
                effectiveImmediateReasonOtherText = immediateReasonOtherText;
            }
        }

        return new TriageAssessment(
                null, encounterId, chiefComplaint, currentSymptoms, allergies, comorbidities, modeOfAmbulation,
                hasPain, effectivePainScore, effectivePainLocation, fallRisk, effectiveFallScore, infectionRisk,
                effectivePregnancyStatus, effectiveGestationalWeeks, priority, effectiveImmediateReason,
                effectiveImmediateReasonOtherText, notes, supersedesId, assessedBy, null);
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new InvalidTriageAssessmentException(field + " is required.");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (isBlank(value)) {
            throw new InvalidTriageAssessmentException(field + " is required.");
        }
    }

    private static void requireInRange(String value, String field, int min, int max) {
        requireNonBlank(value, field);
        if (value.length() < min || value.length() > max) {
            throw new InvalidTriageAssessmentException(field + " must be between " + min + " and " + max + " characters.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
