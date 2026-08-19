package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.DeductionRule;
import com.adags.modules.finance.domain.DeductionRuleRepository;
import com.adags.modules.finance.domain.SalaryRecord;
import com.adags.modules.finance.domain.Staff;
import com.adags.shared.kernel.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Shared by PreviewPayrollBatchService and GeneratePayrollBatchService —
// the preview must show exactly the numbers generate() will actually
// commit, so both go through this one computation. No per-employee
// overtime entry mechanism exists in this item; every run computes with
// zero overtime — the schema (SalaryRecord.overtimeHours/overtimeRate)
// is ready for that later, this is only what's wired today.
final class PayrollComputation {

    private static final BigDecimal ZERO_HOURS = BigDecimal.ZERO;

    private PayrollComputation() {
    }

    static SalaryRecord computeFor(Staff staff, int month, int year, DeductionRuleRepository deductionRuleRepository,
                                    UUID batchId) {
        LocalDate asOfDate = LocalDate.of(year, month, 1);
        // §9.1: employer-paid rules (SDL, WCF, NSSF/PSSSF employer
        // portion) are a hospital expense, never a payslip deduction —
        // excluded here, not just documented.
        List<DeductionRule> employeeDeductedRules = deductionRuleRepository
                .findEffective(staff.employmentType(), asOfDate).stream()
                .filter(rule -> !rule.isEmployerPaid())
                .toList();
        Money basic = staff.basicSalary() != null ? staff.basicSalary() : new Money(0, "TZS");
        Money overtimeRate = new Money(0, basic.currencyCode());
        return SalaryRecord.compute(staff.id(), month, year, basic, ZERO_HOURS, overtimeRate, employeeDeductedRules, batchId);
    }
}
