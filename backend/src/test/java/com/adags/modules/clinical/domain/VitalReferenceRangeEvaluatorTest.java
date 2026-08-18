package com.adags.modules.clinical.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VitalReferenceRangeEvaluatorTest {

    private static final VitalReferenceRange ADULT_TEMPERATURE = new VitalReferenceRange(
            UUID.randomUUID(), VitalType.TEMPERATURE, AgeBand.ADULT, Sex.ANY,
            30.0, 43.0, 35.5, 38.0, LocalDate.now(), null);

    // 06-triage.md §2.2: diastolic_bp has no lower abnormal bound.
    private static final VitalReferenceRange ADULT_DIASTOLIC = new VitalReferenceRange(
            UUID.randomUUID(), VitalType.DIASTOLIC_BP, AgeBand.ADULT, Sex.ANY,
            20.0, 200.0, null, 110.0, LocalDate.now(), null);

    private static final VitalReferenceRange UNCONFIGURED_NEONATE_PULSE = new VitalReferenceRange(
            UUID.randomUUID(), VitalType.PULSE, AgeBand.NEONATE, Sex.ANY,
            null, null, null, null, LocalDate.now(), null);

    @Test
    void withinNormalRangeIsNotAbnormal() {
        VitalEvaluation result = VitalReferenceRangeEvaluator.evaluate(VitalType.TEMPERATURE, 36.8, ADULT_TEMPERATURE);
        assertThat(result.abnormal()).isFalse();
    }

    @Test
    void belowAbnormalLowIsFlaggedButNotRejected() {
        VitalEvaluation result = VitalReferenceRangeEvaluator.evaluate(VitalType.TEMPERATURE, 35.0, ADULT_TEMPERATURE);
        assertThat(result.abnormal()).isTrue();
        assertThat(result.value()).isEqualTo(35.0);
    }

    @Test
    void aboveAbnormalHighIsFlaggedButNotRejected() {
        VitalEvaluation result = VitalReferenceRangeEvaluator.evaluate(VitalType.TEMPERATURE, 39.0, ADULT_TEMPERATURE);
        assertThat(result.abnormal()).isTrue();
    }

    @Test
    void belowImplausibleLowIsRejectedNamingTheBound() {
        assertThatThrownBy(() -> VitalReferenceRangeEvaluator.evaluate(VitalType.TEMPERATURE, 29.9, ADULT_TEMPERATURE))
                .isInstanceOf(ImplausibleVitalException.class)
                .hasMessageContaining("30");
    }

    @Test
    void aboveImplausibleHighIsRejectedNamingTheBound() {
        assertThatThrownBy(() -> VitalReferenceRangeEvaluator.evaluate(VitalType.TEMPERATURE, 43.1, ADULT_TEMPERATURE))
                .isInstanceOf(ImplausibleVitalException.class)
                .hasMessageContaining("43");
    }

    // "A pulse typed as 900 must be corrected, not saved as 250."
    @Test
    void anExtremeOutOfRangeValueIsRejectedNotClamped() {
        VitalReferenceRange adultPulse = new VitalReferenceRange(
                UUID.randomUUID(), VitalType.PULSE, AgeBand.ADULT, Sex.ANY, 20.0, 250.0, 50.0, 110.0, LocalDate.now(), null);
        assertThatThrownBy(() -> VitalReferenceRangeEvaluator.evaluate(VitalType.PULSE, 900, adultPulse))
                .isInstanceOf(ImplausibleVitalException.class)
                .hasMessageContaining("250");
    }

    @Test
    void aOneSidedAbnormalBoundOnlyFlagsItsOwnSide() {
        // diastolic_bp: no lower abnormal bound at all — a very low reading must not be flagged abnormal by that bound.
        VitalEvaluation low = VitalReferenceRangeEvaluator.evaluate(VitalType.DIASTOLIC_BP, 21, ADULT_DIASTOLIC);
        assertThat(low.abnormal()).isFalse();

        VitalEvaluation high = VitalReferenceRangeEvaluator.evaluate(VitalType.DIASTOLIC_BP, 115, ADULT_DIASTOLIC);
        assertThat(high.abnormal()).isTrue();
    }

    // 06-triage.md §2.2 correction: refuse rather than fall back to adult bounds.
    @Test
    void anUnconfiguredBandRefusesToEvaluateRatherThanFallingBackToAdultBounds() {
        assertThatThrownBy(() -> VitalReferenceRangeEvaluator.evaluate(VitalType.PULSE, 140, UNCONFIGURED_NEONATE_PULSE))
                .isInstanceOf(VitalReferenceRangeNotConfiguredException.class)
                .hasMessageContaining("PULSE")
                .hasMessageContaining("NEONATE");
    }

    @Test
    void aMismatchedRangeIsRejectedAsACallerBug() {
        assertThatThrownBy(() -> VitalReferenceRangeEvaluator.evaluate(VitalType.PULSE, 80, ADULT_TEMPERATURE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
