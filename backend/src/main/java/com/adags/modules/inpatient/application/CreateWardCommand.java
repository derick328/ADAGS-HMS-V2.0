package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.GenderRestriction;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.shared.kernel.Money;

import java.util.UUID;

public record CreateWardCommand(
        UUID departmentId,
        String code,
        String name,
        WardClass wardClass,
        GenderRestriction genderRestriction,
        Money dailyRate
) {
}
