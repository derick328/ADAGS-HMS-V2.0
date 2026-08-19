package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.CalculationMethod;
import com.adags.modules.finance.domain.EmploymentType;

import java.math.BigDecimal;
import java.util.UUID;

public record DefineDeductionRuleCommand(
        EmploymentType employmentType,
        String deductionCode,
        CalculationMethod calculationMethod,
        BigDecimal rateOrAmount,
        Long thresholdMinor,
        boolean isEmployerPaid,
        UUID createdBy
) {
}
