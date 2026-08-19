package com.adags.modules.finance.api;

import com.adags.modules.finance.domain.CalculationMethod;
import com.adags.modules.finance.domain.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DefineDeductionRuleRequest(
        @NotNull EmploymentType employmentType,
        @NotBlank String deductionCode,
        @NotNull CalculationMethod calculationMethod,
        @NotNull BigDecimal rateOrAmount,
        Long thresholdMinor,
        boolean isEmployerPaid
) {
}
