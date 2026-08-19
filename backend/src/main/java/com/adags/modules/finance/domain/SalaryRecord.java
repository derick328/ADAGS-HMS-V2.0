package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 18-admin.md §9.3. gross = basic + overtime; net = gross - sum(deductions).
// deductions only ever carries employee-deducted lines — §9.1: employer-
// paid rules (SDL, WCF, NSSF/PSSSF employer portion) are a hospital
// expense, never a payslip line, so compute() must only ever be handed
// the non-employer-paid rules that apply to this staff member's
// employment type; that filtering is the caller's responsibility
// (GeneratePayrollBatchService), not enforced here structurally.
public record SalaryRecord(
        UUID id,
        UUID staffId,
        int month,
        int year,
        Money basic,
        BigDecimal overtimeHours,
        Money overtimeRate,
        Money gross,
        List<DeductionLine> deductions,
        Money net,
        PaymentMethod paymentMethod,
        SalaryPaymentStatus paymentStatus,
        Instant paidAt,
        String notes,
        UUID batchId,
        Instant createdAt
) {

    public static SalaryRecord compute(UUID staffId, int month, int year, Money basic, BigDecimal overtimeHours,
                                        Money overtimeRate, List<DeductionRule> employeeDeductedRules, UUID batchId) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12.");
        }
        String currencyCode = basic.currencyCode();
        long overtimeMinor = BigDecimal.valueOf(overtimeRate.amountMinor())
                .multiply(overtimeHours)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long grossMinor = basic.amountMinor() + overtimeMinor;

        List<DeductionLine> lines = employeeDeductedRules.stream()
                .map(rule -> new DeductionLine(rule.deductionCode(),
                        new Money(rule.computeAmount(grossMinor), currencyCode), rule.id()))
                .toList();
        long deductionsMinor = lines.stream().mapToLong(l -> l.amount().amountMinor()).sum();
        long netMinor = grossMinor - deductionsMinor;
        if (netMinor < 0) {
            throw new IllegalArgumentException("Deductions (" + deductionsMinor + ") exceed gross (" + grossMinor + ") for staff " + staffId + ".");
        }

        return new SalaryRecord(null, staffId, month, year, basic, overtimeHours, overtimeRate,
                new Money(grossMinor, currencyCode), lines, new Money(netMinor, currencyCode),
                null, SalaryPaymentStatus.PENDING, null, null, batchId, null);
    }
}
