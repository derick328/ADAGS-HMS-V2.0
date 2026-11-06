package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.SpecimenQuality;

import java.util.UUID;

public record CollectSpecimenCommand(
        UUID labOrderId,
        SpecimenQuality quality,
        String qualityNote,
        String sampleType,
        String sampleTypeOverrideReason,
        UUID actorUserId
) {
    public CollectSpecimenCommand(UUID labOrderId, SpecimenQuality quality, String qualityNote, UUID actorUserId) {
        this(labOrderId, quality, qualityNote, null, null, actorUserId);
    }
}
