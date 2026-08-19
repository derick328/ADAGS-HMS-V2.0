package com.adags.modules.finance.api;

import com.adags.modules.finance.domain.CalculationMethod;
import com.adags.modules.finance.domain.DeductionRule;
import com.adags.modules.finance.domain.EmploymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DeductionRuleResponse(
        UUID id,
        EmploymentType employmentType,
        String deductionCode,
        CalculationMethod calculationMethod,
        BigDecimal rateOrAmount,
        Long thresholdMinor,
        String thresholdCurrencyCode,
        boolean isEmployerPaid,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        UUID createdBy,
        Instant createdAt
) {

    public static DeductionRuleResponse from(DeductionRule rule) {
        return new DeductionRuleResponse(rule.id(), rule.employmentType(), rule.deductionCode(),
                rule.calculationMethod(), rule.rateOrAmount(),
                rule.threshold() == null ? null : rule.threshold().amountMinor(),
                rule.threshold() == null ? null : rule.threshold().currencyCode(),
                rule.isEmployerPaid(), rule.effectiveFrom(), rule.effectiveTo(), rule.createdBy(), rule.createdAt());
    }
}
