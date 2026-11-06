package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.UUID;

// 15-lab-technician.md §4.1-§4.3. sampleType/containerType are copied from
// the ordered LabTest, never client-supplied ("from the catalogue's
// expected specimen type, not free text" — §4.2). accessionNo is allocated
// from GaplessNumberAllocator, gapless per facility per year, at creation.
//
// collectedBy/receivedBy and their timestamps are set to the same
// actor/instant in this phase's single "accept" action (see
// V21__lab_order_and_specimen.sql) — received* stay populated from
// creation rather than modelling a later, separate receipt step.
public record Specimen(
        UUID id,
        UUID labOrderId,
        String accessionNo,
        String sampleType,
        String containerType,
        UUID collectedBy,
        Instant collectedAt,
        UUID receivedBy,
        Instant receivedAt,
        SpecimenQuality quality,
        String qualityNote,
        SpecimenStatus status,
        String rejectionReason,
        Instant createdAt,
        String sampleTypeOverrideReason
) {

    public static Specimen accept(UUID labOrderId, String accessionNo, String sampleType, String containerType,
                                   UUID actorUserId, Instant now, SpecimenQuality quality, String qualityNote) {
        return accept(labOrderId, accessionNo, sampleType, containerType, actorUserId, now,
                quality, qualityNote, null);
    }

    public static Specimen accept(UUID labOrderId, String accessionNo, String sampleType, String containerType,
                                   UUID actorUserId, Instant now, SpecimenQuality quality, String qualityNote,
                                   String sampleTypeOverrideReason) {
        boolean adequate = quality == SpecimenQuality.ADEQUATE;
        return new Specimen(null, labOrderId, accessionNo, sampleType, containerType,
                actorUserId, now, actorUserId, now, quality, qualityNote,
                adequate ? SpecimenStatus.IN_PROGRESS : SpecimenStatus.REJECTED,
                adequate ? null : qualityNote, null, sampleTypeOverrideReason);
    }
    public Specimen(UUID id, UUID labOrderId, String accessionNo, String sampleType, String containerType,
                    UUID collectedBy, Instant collectedAt, UUID receivedBy, Instant receivedAt,
                    SpecimenQuality quality, String qualityNote, SpecimenStatus status,
                    String rejectionReason, Instant createdAt) {
        this(id, labOrderId, accessionNo, sampleType, containerType, collectedBy, collectedAt, receivedBy,
                receivedAt, quality, qualityNote, status, rejectionReason, createdAt, null);
    }
}
