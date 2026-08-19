package com.adags.modules.insurance;

import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.ServiceCategory;
import com.adags.shared.kernel.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Pure domain-layer validation — no Spring context, backend/CLAUDE.md
// "Testing": "Domain: plain JUnit, no Spring context."
class SchemeCoverageRuleTest {

    @Test
    void percentageWithoutAValueIsRejected() {
        assertThatThrownBy(() -> SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE, null, false, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void percentageAbove100IsRejected() {
        assertThatThrownBy(() -> SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE,
                BigDecimal.valueOf(120), false, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePercentageIsRejected() {
        assertThatThrownBy(() -> SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE,
                BigDecimal.valueOf(-1), false, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fixedAmountWithoutAValueIsRejected() {
        assertThatThrownBy(() -> SchemeCoverageRule.forServiceItem(
                UUID.randomUUID(), UUID.randomUUID(), CoverageType.FIXED_AMOUNT, null, false, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fullAndExcludedNeedNoValue() {
        SchemeCoverageRule full = SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.LAB, CoverageType.FULL, null, false, UUID.randomUUID());
        SchemeCoverageRule excluded = SchemeCoverageRule.forServiceItem(
                UUID.randomUUID(), UUID.randomUUID(), CoverageType.EXCLUDED, null, false, UUID.randomUUID());

        assertThat(full.coverageValue()).isNull();
        assertThat(excluded.coverageValue()).isNull();
    }

    @Test
    void aCategoryRuleCarriesNoServiceItem() {
        SchemeCoverageRule rule = SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.PHARMACY, CoverageType.PERCENTAGE, BigDecimal.valueOf(80), true, UUID.randomUUID());

        assertThat(rule.serviceCategory()).isEqualTo(ServiceCategory.PHARMACY);
        assertThat(rule.serviceItemId()).isNull();
        assertThat(rule.requiresPreauth()).isTrue();
    }

    @Test
    void anItemSpecificRuleCarriesNoCategory() {
        UUID serviceItemId = UUID.randomUUID();
        SchemeCoverageRule rule = SchemeCoverageRule.forServiceItem(
                UUID.randomUUID(), serviceItemId, CoverageType.FIXED_AMOUNT, BigDecimal.valueOf(50_000), false, UUID.randomUUID());

        assertThat(rule.serviceItemId()).isEqualTo(serviceItemId);
        assertThat(rule.serviceCategory()).isNull();
    }

    // Phase 7 item 3 part 2: computeSplit — the pure arithmetic behind
    // the InvoiceLine split.

    @Test
    void fullMeansInsuranceCoversTheWholeGross() {
        SchemeCoverageRule rule = SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.LAB, CoverageType.FULL, null, false, UUID.randomUUID());

        SchemeCoverageRule.Split split = rule.computeSplit(new Money(50_000, "TZS"));

        assertThat(split.insurancePortion()).isEqualTo(new Money(50_000, "TZS"));
        assertThat(split.patientPortion()).isEqualTo(new Money(0, "TZS"));
    }

    @Test
    void excludedMeansThePatientPaysEverything() {
        SchemeCoverageRule rule = SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.PHARMACY, CoverageType.EXCLUDED, null, false, UUID.randomUUID());

        SchemeCoverageRule.Split split = rule.computeSplit(new Money(50_000, "TZS"));

        assertThat(split.insurancePortion()).isEqualTo(new Money(0, "TZS"));
        assertThat(split.patientPortion()).isEqualTo(new Money(50_000, "TZS"));
    }

    @Test
    void percentageSplitsProportionallyAndTheHalvesSumToGross() {
        SchemeCoverageRule rule = SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE, BigDecimal.valueOf(80), false, UUID.randomUUID());

        SchemeCoverageRule.Split split = rule.computeSplit(new Money(10_000, "TZS"));

        assertThat(split.insurancePortion()).isEqualTo(new Money(8_000, "TZS"));
        assertThat(split.patientPortion()).isEqualTo(new Money(2_000, "TZS"));
    }

    // A percentage that doesn't divide evenly into whole minor units must
    // still leave the two halves summing exactly to gross —
    // patientPortion is always the remainder, never independently
    // rounded, so there is never an unaccounted-for minor unit.
    @Test
    void percentageRoundingNeverLosesOrGainsAMinorUnit() {
        SchemeCoverageRule rule = SchemeCoverageRule.forCategory(
                UUID.randomUUID(), ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE, BigDecimal.valueOf(33), false, UUID.randomUUID());

        // 10 * 33% = 3.3, rounds HALF_UP to 3 — not an exact division.
        SchemeCoverageRule.Split split = rule.computeSplit(new Money(10, "TZS"));

        assertThat(split.insurancePortion().amountMinor() + split.patientPortion().amountMinor()).isEqualTo(10);
        assertThat(split.insurancePortion()).isEqualTo(new Money(3, "TZS"));
        assertThat(split.patientPortion()).isEqualTo(new Money(7, "TZS"));
    }

    @Test
    void fixedAmountIsCappedAtGross() {
        SchemeCoverageRule rule = SchemeCoverageRule.forServiceItem(
                UUID.randomUUID(), UUID.randomUUID(), CoverageType.FIXED_AMOUNT, BigDecimal.valueOf(100_000), false, UUID.randomUUID());

        SchemeCoverageRule.Split split = rule.computeSplit(new Money(30_000, "TZS"));

        assertThat(split.insurancePortion()).isEqualTo(new Money(30_000, "TZS"));
        assertThat(split.patientPortion()).isEqualTo(new Money(0, "TZS"));
    }

    @Test
    void fixedAmountBelowGrossLeavesARemainderForThePatient() {
        SchemeCoverageRule rule = SchemeCoverageRule.forServiceItem(
                UUID.randomUUID(), UUID.randomUUID(), CoverageType.FIXED_AMOUNT, BigDecimal.valueOf(20_000), false, UUID.randomUUID());

        SchemeCoverageRule.Split split = rule.computeSplit(new Money(30_000, "TZS"));

        assertThat(split.insurancePortion()).isEqualTo(new Money(20_000, "TZS"));
        assertThat(split.patientPortion()).isEqualTo(new Money(10_000, "TZS"));
    }
}
