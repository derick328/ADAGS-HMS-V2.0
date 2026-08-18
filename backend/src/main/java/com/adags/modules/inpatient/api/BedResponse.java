package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedStatus;

import java.time.Instant;
import java.util.UUID;

public record BedResponse(UUID id, UUID wardId, String label, String bedType, BedStatus status, Instant createdAt) {
    public static BedResponse from(Bed bed) {
        return new BedResponse(bed.id(), bed.wardId(), bed.label(), bed.bedType(), bed.status(), bed.createdAt());
    }
}
