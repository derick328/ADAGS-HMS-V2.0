package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 18-admin.md §9.3: gross = basic + overtime; net = gross - sum(deductions).
// Pure domain logic, no Spring context (backend/CLAUDE.md "Testing").
class SalaryRecordTest {

    private static final UUID STAFF_ID = UUID.randomUUID();

    @Test
    void grossIsBasicPlusOvertimeAndNetSubtractsDeductions() {
        DeductionRule paye = DeductionRule.define(EmploymentType.PERMANENT, "PAYE", CalculationMethod.PERCENTAGE,
                new BigDecimal("10"), null, false, UUID.randomUUID());

        SalaryRecord record = SalaryRecord.compute(STAFF_ID, 8, 2026, new Money(1_000_000, "TZS"),
                new BigDecimal("5"), new Money(10_000, "TZS"), List.of(paye), null);

        assertThat(record.gross()).isEqualTo(new Money(1_050_000, "TZS"));
        assertThat(record.deductions()).hasSize(1);
        assertThat(record.deductions().get(0).amount()).isEqualTo(new Money(105_000, "TZS"));
        assertThat(record.net()).isEqualTo(new Money(945_000, "TZS"));
        assertThat(record.paymentStatus()).isEqualTo(SalaryPaymentStatus.PENDING);
    }

    @Test
    void deductionsExceedingGrossAreRejectedRatherThanProducingANegativeNet() {
        DeductionRule confiscatory = DeductionRule.define(EmploymentType.PERMANENT, "OVER", CalculationMethod.FIXED,
                new BigDecimal("2000000"), null, false, UUID.randomUUID());

        assertThatThrownBy(() -> SalaryRecord.compute(STAFF_ID, 8, 2026, new Money(1_000_000, "TZS"),
                BigDecimal.ZERO, new Money(0, "TZS"), List.of(confiscatory), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deductionLinesCarryTheRuleIdForHistoricalReconstruction() {
        DeductionRule rule = DeductionRule.define(EmploymentType.PERMANENT, "NHIF_EMPLOYEE", CalculationMethod.FIXED,
                new BigDecimal("25000"), null, false, UUID.randomUUID());
        DeductionRule withId = new DeductionRule(UUID.randomUUID(), rule.employmentType(), rule.deductionCode(),
                rule.calculationMethod(), rule.rateOrAmount(), rule.threshold(), rule.isEmployerPaid(),
                rule.effectiveFrom(), rule.effectiveTo(), rule.createdBy(), rule.createdAt());

        SalaryRecord record = SalaryRecord.compute(STAFF_ID, 8, 2026, new Money(1_000_000, "TZS"),
                BigDecimal.ZERO, new Money(0, "TZS"), List.of(withId), null);

        assertThat(record.deductions().get(0).ruleId()).isEqualTo(withId.id());
        assertThat(record.deductions().get(0).code()).isEqualTo("NHIF_EMPLOYEE");
    }

    @Test
    void anInvalidMonthIsRejected() {
        assertThatThrownBy(() -> SalaryRecord.compute(STAFF_ID, 13, 2026, new Money(1_000_000, "TZS"),
                BigDecimal.ZERO, new Money(0, "TZS"), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
