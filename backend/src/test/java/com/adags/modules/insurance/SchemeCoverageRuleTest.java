package com.adags.modules.insurance;

import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.ServiceCategory;
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
}
