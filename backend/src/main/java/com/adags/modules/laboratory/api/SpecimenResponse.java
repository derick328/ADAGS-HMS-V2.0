package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenQuality;
import com.adags.modules.laboratory.domain.SpecimenStatus;

import java.time.Instant;
import java.util.UUID;

public record SpecimenResponse(
        UUID id,
        UUID labOrderId,
        String accessionNo,
        String sampleType,
        String containerType,
        SpecimenQuality quality,
        SpecimenStatus status,
        String rejectionReason,
        Instant collectedAt
) {

    public static SpecimenResponse from(Specimen specimen) {
        return new SpecimenResponse(specimen.id(), specimen.labOrderId(), specimen.accessionNo(),
                specimen.sampleType(), specimen.containerType(), specimen.quality(), specimen.status(),
                specimen.rejectionReason(), specimen.collectedAt());
    }
}
