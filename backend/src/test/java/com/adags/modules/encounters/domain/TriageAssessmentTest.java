package com.adags.modules.encounters.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 06-triage.md §2 — construction-time validation only. Persistence is
// TriageAssessmentRepositoryTest; this covers what create() decides before
// anything reaches SQL.
class TriageAssessmentTest {

    private static final UUID ENCOUNTER_ID = UUID.randomUUID();
    private static final UUID ASSESSED_BY = UUID.randomUUID();

    @Test
    void aMinimalValidAssessmentConstructs() {
        TriageAssessment assessment = create(builder());
        assertThat(assessment.hasPain()).isFalse();
        assertThat(assessment.painScore()).isNull();
        assertThat(assessment.priority()).isEqualTo(TriagePriority.NON_URGENT);
    }

    // §2.5: "the server discards any submitted values when has_pain is false".
    @Test
    void painFieldsAreDiscardedWhenHasPainIsFalse() {
        Builder b = builder();
        b.hasPain = false;
        b.painScore = 7;
        b.painLocation = "left knee";

        TriageAssessment assessment = create(b);

        assertThat(assessment.painScore()).isNull();
        assertThat(assessment.painLocation()).isNull();
    }

    @Test
    void painScoreIsOptionalWhenHasPainIsTrue() {
        Builder b = builder();
        b.hasPain = true;
        b.painLocation = "left knee";

        TriageAssessment assessment = create(b);
        assertThat(assessment.hasPain()).isTrue();
        assertThat(assessment.painScore()).isNull();
        assertThat(assessment.painLocation()).isEqualTo("left knee");
    }

    @Test
    void painLocationIsRequiredWhenHasPainIsTrue() {
        Builder b = builder();
        b.hasPain = true;
        b.painScore = 5;

        assertThatThrownBy(() -> create(b))
                .isInstanceOf(InvalidTriageAssessmentException.class)
                .hasMessageContaining("pain_location");
    }

    @Test
    void painScoreOutOfZeroToTenRangeIsRejected() {
        Builder b = builder();
        b.hasPain = true;
        b.painScore = 11;
        b.painLocation = "left knee";

        assertThatThrownBy(() -> create(b)).isInstanceOf(InvalidTriageAssessmentException.class);
    }

    @Test
    void bothPainFieldsPersistWhenHasPainIsTrue() {
        Builder b = builder();
        b.hasPain = true;
        b.painScore = 5;
        b.painLocation = "left knee";

        TriageAssessment assessment = create(b);

        assertThat(assessment.painScore()).isEqualTo(5);
        assertThat(assessment.painLocation()).isEqualTo("left knee");
    }

    // §2.6: identical rule to pain, for fall risk.
    @Test
    void fallScoreIsDiscardedWhenFallRiskIsFalse() {
        Builder b = builder();
        b.fallRisk = false;
        b.fallScore = 9;

        TriageAssessment assessment = create(b);

        assertThat(assessment.fallScore()).isNull();
    }

    @Test
    void fallScoreIsOptionalWhenFallRiskIsTrue() {
        Builder b = builder();
        b.fallRisk = true;

        TriageAssessment assessment = create(b);
        assertThat(assessment.fallRisk()).isTrue();
        assertThat(assessment.fallScore()).isNull();
    }

    // §4.1/§4.2: IMMEDIATE always carries a reason from the fixed list.
    @Test
    void immediateReasonIsRequiredWhenPriorityIsImmediate() {
        Builder b = builder();
        b.priority = TriagePriority.IMMEDIATE;

        assertThatThrownBy(() -> create(b))
                .isInstanceOf(InvalidTriageAssessmentException.class)
                .hasMessageContaining("immediate_reason");
    }

    @Test
    void immediateReasonIsDiscardedForNonImmediatePriority() {
        Builder b = builder();
        b.priority = TriagePriority.URGENT;
        b.immediateReason = ImmediateReason.SEVERE_RESPIRATORY_DISTRESS;

        TriageAssessment assessment = create(b);

        assertThat(assessment.immediateReason()).isNull();
    }

    @Test
    void otherImmediateReasonRequiresFreeText() {
        Builder b = builder();
        b.priority = TriagePriority.IMMEDIATE;
        b.immediateReason = ImmediateReason.OTHER;

        assertThatThrownBy(() -> create(b))
                .isInstanceOf(InvalidTriageAssessmentException.class)
                .hasMessageContaining("immediate_reason_other_text");
    }

    @Test
    void nonOtherImmediateReasonRejectsFreeText() {
        // Free text is only meaningful for OTHER — discarded, not an error, for the fixed-list reasons.
        Builder b = builder();
        b.priority = TriagePriority.IMMEDIATE;
        b.immediateReason = ImmediateReason.ACTIVE_CONVULSION;
        b.immediateReasonOtherText = "should be ignored";

        TriageAssessment assessment = create(b);

        assertThat(assessment.immediateReason()).isEqualTo(ImmediateReason.ACTIVE_CONVULSION);
        assertThat(assessment.immediateReasonOtherText()).isNull();
    }

    // §2.7: auto-appears / required only within the configured age bounds.
    @Test
    void pregnancyStatusIsRequiredWhenPromptIsApplicable() {
        Builder b = builder();
        b.pregnancyPromptApplicable = true;

        assertThatThrownBy(() -> create(b))
                .isInstanceOf(InvalidTriageAssessmentException.class)
                .hasMessageContaining("pregnancy_status");
    }

    @Test
    void pregnancyFieldsAreDiscardedWhenPromptIsNotApplicable() {
        Builder b = builder();
        b.pregnancyPromptApplicable = false;
        b.pregnancyStatus = PregnancyStatus.YES;
        b.gestationalWeeks = 20;

        TriageAssessment assessment = create(b);

        assertThat(assessment.pregnancyStatus()).isNull();
        assertThat(assessment.gestationalWeeks()).isNull();
    }

    @Test
    void chiefComplaintShorterThanThreeCharactersIsRejected() {
        Builder b = builder();
        b.chiefComplaint = "hi";

        assertThatThrownBy(() -> create(b)).isInstanceOf(InvalidTriageAssessmentException.class);
    }

    @Test
    void complaintAndMedicalHistoryMayBeOmitted() {
        Builder b = builder();
        b.chiefComplaint = null;
        b.currentSymptoms = null;
        b.allergies = null;
        b.comorbidities = null;
        b.modeOfAmbulation = null;

        TriageAssessment assessment = create(b);

        assertThat(assessment.chiefComplaint()).isNull();
        assertThat(assessment.allergies()).isNull();
        assertThat(assessment.modeOfAmbulation()).isNull();
    }

    @Test
    void notesLongerThanTwoThousandCharactersIsRejected() {
        Builder b = builder();
        b.notes = "x".repeat(2001);

        assertThatThrownBy(() -> create(b)).isInstanceOf(InvalidTriageAssessmentException.class);
    }

    @Test
    void emptyNotesIsAllowedPerTheRecommendedOptionalRule() {
        Builder b = builder();
        b.notes = null;

        TriageAssessment assessment = create(b);
        assertThat(assessment.notes()).isNull();
    }

    private static Builder builder() {
        return new Builder();
    }

    private static TriageAssessment create(Builder b) {
        return TriageAssessment.create(
                ENCOUNTER_ID, b.chiefComplaint, b.currentSymptoms, b.allergies, b.comorbidities,
                b.modeOfAmbulation, b.hasPain, b.painScore, b.painLocation,
                b.fallRisk, b.fallScore, false,
                b.pregnancyPromptApplicable, b.pregnancyStatus, b.gestationalWeeks,
                b.priority, b.immediateReason, b.immediateReasonOtherText,
                b.notes, null, ASSESSED_BY);
    }

    private static final class Builder {
        String chiefComplaint = "Fever and headache";
        String currentSymptoms = "Started this morning";
        String allergies = "NKDA";
        String comorbidities = "None";
        ModeOfAmbulation modeOfAmbulation = ModeOfAmbulation.WALKING;
        boolean hasPain = false;
        Integer painScore;
        String painLocation;
        boolean fallRisk = false;
        Integer fallScore;
        boolean pregnancyPromptApplicable = false;
        PregnancyStatus pregnancyStatus;
        Integer gestationalWeeks;
        TriagePriority priority = TriagePriority.NON_URGENT;
        ImmediateReason immediateReason;
        String immediateReasonOtherText;
        String notes;
    }
}
