package com.adags.modules.encounters.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// 06-triage.md §2.7: "Auto-appears when gender = FEMALE and age is 12-50."
class PregnancyPromptPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);

    @Test
    void appliesToAFemaleWithinBounds() {
        LocalDate dob = TODAY.minusYears(25);
        assertThat(PregnancyPromptPolicy.isApplicable("FEMALE", dob, TODAY, 12, 50)).isTrue();
    }

    @Test
    void doesNotApplyToAMale() {
        LocalDate dob = TODAY.minusYears(25);
        assertThat(PregnancyPromptPolicy.isApplicable("MALE", dob, TODAY, 12, 50)).isFalse();
    }

    @Test
    void doesNotApplyBelowTheLowerBound() {
        LocalDate dob = TODAY.minusYears(11);
        assertThat(PregnancyPromptPolicy.isApplicable("FEMALE", dob, TODAY, 12, 50)).isFalse();
    }

    @Test
    void appliesAtExactlyTheLowerBound() {
        LocalDate dob = TODAY.minusYears(12);
        assertThat(PregnancyPromptPolicy.isApplicable("FEMALE", dob, TODAY, 12, 50)).isTrue();
    }

    @Test
    void appliesAtExactlyTheUpperBound() {
        LocalDate dob = TODAY.minusYears(50);
        assertThat(PregnancyPromptPolicy.isApplicable("FEMALE", dob, TODAY, 12, 50)).isTrue();
    }

    @Test
    void doesNotApplyAboveTheUpperBound() {
        LocalDate dob = TODAY.minusYears(51);
        assertThat(PregnancyPromptPolicy.isApplicable("FEMALE", dob, TODAY, 12, 50)).isFalse();
    }

    @Test
    void doesNotApplyToOtherOrUnspecifiedGender() {
        LocalDate dob = TODAY.minusYears(25);
        assertThat(PregnancyPromptPolicy.isApplicable("OTHER", dob, TODAY, 12, 50)).isFalse();
        assertThat(PregnancyPromptPolicy.isApplicable("PREFER_NOT_TO_SAY", dob, TODAY, 12, 50)).isFalse();
    }
}
