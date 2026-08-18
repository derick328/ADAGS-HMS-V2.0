package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.GenderRestriction;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardClass;

import java.time.Instant;
import java.util.UUID;

public record WardResponse(
        UUID id,
        UUID facilityId,
        UUID departmentId,
        String code,
        String name,
        WardClass wardClass,
        GenderRestriction genderRestriction,
        long dailyRateMinor,
        String currencyCode,
        boolean active,
        Instant createdAt
) {
    public static WardResponse from(Ward ward) {
        return new WardResponse(ward.id(), ward.facilityId(), ward.departmentId(), ward.code(), ward.name(),
                ward.wardClass(), ward.genderRestriction(), ward.dailyRate().amountMinor(),
                ward.dailyRate().currencyCode(), ward.active(), ward.createdAt());
    }
}
