package com.adags.modules.laboratory.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabResultFlagEvaluatorTest {

    // Loosely modelled on adult potassium: normal 3.5-5.0, critical outside 2.5-6.5,
    // implausible outside 0-15 — "a potassium of 70 is a decimal error, not a finding."
    private static final LabReferenceRange POTASSIUM_ADULT = new LabReferenceRange(
            UUID.randomUUID(), UUID.randomUUID(), 216, null, Sex.ANY,
            0.0, 15.0, 3.5, 5.0, 2.5, 6.5, LocalDate.now(), null, UUID.randomUUID(), null);

    private static final LabReferenceRange UNCONFIGURED = new LabReferenceRange(
            UUID.randomUUID(), UUID.randomUUID(), 0, 1, Sex.ANY,
            null, null, null, null, null, null, LocalDate.now(), null, UUID.randomUUID(), null);

    @Test
    void withinNormalRangeIsNormal() {
        assertThat(LabResultFlagEvaluator.evaluate("K", 4.2, POTASSIUM_ADULT)).isEqualTo(ResultFlag.NORMAL);
    }

    @Test
    void belowNormalButAboveCriticalIsLow() {
        assertThat(LabResultFlagEvaluator.evaluate("K", 3.0, POTASSIUM_ADULT)).isEqualTo(ResultFlag.LOW);
    }

    @Test
    void aboveNormalButBelowCriticalIsHigh() {
        assertThat(LabResultFlagEvaluator.evaluate("K", 5.5, POTASSIUM_ADULT)).isEqualTo(ResultFlag.HIGH);
    }

    @Test
    void belowCriticalLowIsCriticalNotJustLow() {
        assertThat(LabResultFlagEvaluator.evaluate("K", 2.0, POTASSIUM_ADULT)).isEqualTo(ResultFlag.CRITICAL);
    }

    @Test
    void aboveCriticalHighIsCriticalNotJustHigh() {
        assertThat(LabResultFlagEvaluator.evaluate("K", 7.0, POTASSIUM_ADULT)).isEqualTo(ResultFlag.CRITICAL);
    }

    // §5.3: "A potassium of 70 is a decimal error, not a finding" — rejected, not flagged CRITICAL.
    @Test
    void beyondImplausibleBoundsIsRejectedNotFlagged() {
        assertThatThrownBy(() -> LabResultFlagEvaluator.evaluate("K", 70.0, POTASSIUM_ADULT))
                .isInstanceOf(ImplausibleResultValueException.class)
                .hasMessageContaining("15");
    }

    @Test
    void belowImplausibleLowIsRejected() {
        assertThatThrownBy(() -> LabResultFlagEvaluator.evaluate("K", -1.0, POTASSIUM_ADULT))
                .isInstanceOf(ImplausibleResultValueException.class)
                .hasMessageContaining("0");
    }

    @Test
    void anUnconfiguredRangeRefusesToEvaluate() {
        assertThatThrownBy(() -> LabResultFlagEvaluator.evaluate("K", 4.0, UNCONFIGURED))
                .isInstanceOf(LabReferenceRangeNotConfiguredException.class);
    }
}
