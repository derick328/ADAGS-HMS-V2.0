package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.WoundCare;

import java.time.Instant;
import java.util.UUID;

public record WoundCareResponse(
        UUID id,
        UUID admissionId,
        String appearance,
        boolean dressingChanged,
        String dressingType,
        boolean signsOfInfection,
        String infectionDescription,
        String remarks,
        UUID recordedBy,
        Instant recordedAt
) {
    public static WoundCareResponse from(WoundCare woundCare) {
        return new WoundCareResponse(woundCare.id(), woundCare.admissionId(), woundCare.appearance(),
                woundCare.dressingChanged(), woundCare.dressingType(), woundCare.signsOfInfection(),
                woundCare.infectionDescription(), woundCare.remarks(), woundCare.recordedBy(), woundCare.recordedAt());
    }
}
