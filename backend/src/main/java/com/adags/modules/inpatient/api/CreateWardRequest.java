package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.GenderRestriction;
import com.adags.modules.inpatient.domain.WardClass;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record CreateWardRequest(
        @NotNull UUID departmentId,
        @NotNull String code,
        @NotNull String name,
        @NotNull WardClass wardClass,
        /** Null means unrestricted/mixed. */
        GenderRestriction genderRestriction,
        @NotNull @PositiveOrZero Long dailyRateMinor,
        @NotNull String currencyCode
) {
}
