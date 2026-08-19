package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 18-admin.md §9: "Deductions varying by employment type means the
// system calculates payroll rather than recording payments made."
// Versioned like SchemeCoverageRule/PriceListEntry — a payslip generated
// for August must resolve against whichever rule applied in August.
//
// ⚠ §9.1: isEmployerPaid separates employee deductions (PAYE, NSSF/PSSSF
// employee, NHIF employee — reduce net pay) from employer costs (SDL,
// WCF, NSSF/PSSSF employer — a hospital expense, never a payslip line).
// SalaryRecord computation must only ever apply the employee-deducted
// rules to net pay.
public record DeductionRule(
        UUID id,
        EmploymentType employmentType,
        String deductionCode,
        CalculationMethod calculationMethod,
        /** 0-100 for PERCENTAGE, minor units for FIXED. BAND is modelled but not computable here — see computeAmount. */
        BigDecimal rateOrAmount,
        Money threshold,
        boolean isEmployerPaid,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        UUID createdBy,
        Instant createdAt
) {

    public static DeductionRule define(EmploymentType employmentType, String deductionCode,
                                        CalculationMethod calculationMethod, BigDecimal rateOrAmount,
                                        Money threshold, boolean isEmployerPaid, UUID createdBy) {
        if (deductionCode == null || deductionCode.isBlank()) {
            throw new IllegalArgumentException("deductionCode is required.");
        }
        if (rateOrAmount == null) {
            throw new IllegalArgumentException("rateOrAmount is required.");
        }
        if (calculationMethod == CalculationMethod.PERCENTAGE
                && (rateOrAmount.signum() < 0 || rateOrAmount.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("A PERCENTAGE rateOrAmount must be between 0 and 100.");
        }
        return new DeductionRule(null, employmentType, deductionCode, calculationMethod, rateOrAmount, threshold,
                isEmployerPaid, LocalDate.now(), null, createdBy, null);
    }

    /**
     * The minor-unit amount this rule deducts from grossMinor. BAND is
     * deliberately unsupported — 18-admin.md §9.2 leaves banded
     * calculation an open question pending confirmation with the
     * hospital's accountant; computing a guessed band here would be the
     * same under-withholding risk the brief warns against for a wrong
     * seeded rate.
     */
    public long computeAmount(long grossMinor) {
        return switch (calculationMethod) {
            case PERCENTAGE -> BigDecimal.valueOf(grossMinor)
                    .multiply(rateOrAmount)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            case FIXED -> rateOrAmount.setScale(0, RoundingMode.HALF_UP).longValueExact();
            case BAND -> throw new UnsupportedOperationException(
                    "BAND calculation is not implemented — confirm banded rates with the hospital's accountant first (18-admin.md §9.2).");
        };
    }
}
