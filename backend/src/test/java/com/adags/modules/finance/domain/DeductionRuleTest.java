package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 18-admin.md §9.2: PERCENTAGE and FIXED are computable now; BAND is a
// modelled-but-unimplemented method pending accountant confirmation.
// Pure domain logic, no Spring context (backend/CLAUDE.md "Testing").
class DeductionRuleTest {

    @Test
    void percentageComputesHalfUpOfGross() {
        DeductionRule rule = DeductionRule.define(EmploymentType.PERMANENT, "PAYE", CalculationMethod.PERCENTAGE,
                new BigDecimal("15"), null, false, UUID.randomUUID());

        assertThat(rule.computeAmount(1_000_000)).isEqualTo(150_000);
        // 333,333 * 0.15 = 49,999.95 -> rounds to 50,000 HALF_UP
        assertThat(rule.computeAmount(333_333)).isEqualTo(50_000);
    }

    @Test
    void fixedIgnoresGrossAndRoundsTheConfiguredAmount() {
        DeductionRule rule = DeductionRule.define(EmploymentType.PERMANENT, "NHIF_EMPLOYEE", CalculationMethod.FIXED,
                new BigDecimal("25000"), null, false, UUID.randomUUID());

        assertThat(rule.computeAmount(1_000_000)).isEqualTo(25_000);
        assertThat(rule.computeAmount(50_000)).isEqualTo(25_000);
    }

    @Test
    void bandThrowsRatherThanGuessingACalculation() {
        DeductionRule rule = DeductionRule.define(EmploymentType.PERMANENT, "NSSF", CalculationMethod.BAND,
                BigDecimal.ONE, null, false, UUID.randomUUID());

        assertThatThrownBy(() -> rule.computeAmount(1_000_000)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void definingWithAPercentageOver100IsRejected() {
        assertThatThrownBy(() -> DeductionRule.define(EmploymentType.PERMANENT, "PAYE", CalculationMethod.PERCENTAGE,
                new BigDecimal("101"), null, false, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definingWithoutADeductionCodeIsRejected() {
        assertThatThrownBy(() -> DeductionRule.define(EmploymentType.PERMANENT, " ", CalculationMethod.FIXED,
                BigDecimal.TEN, null, false, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void employerPaidFlagIsCarriedThroughUnchanged() {
        DeductionRule rule = DeductionRule.define(EmploymentType.PERMANENT, "SDL", CalculationMethod.PERCENTAGE,
                new BigDecimal("4"), new Money(0, "TZS"), true, UUID.randomUUID());

        assertThat(rule.isEmployerPaid()).isTrue();
    }
}
