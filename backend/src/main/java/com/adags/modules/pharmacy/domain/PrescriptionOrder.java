package com.adags.modules.pharmacy.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §12, D1: no medicine is dispensed without a doctor's
// prescription. The header — see PrescriptionLine for the gated,
// quantity-tracked, per-item row.
public record PrescriptionOrder(
        UUID id,
        UUID encounterId,
        UUID patientId,
        UUID facilityId,
        UUID prescribedBy,
        Instant prescribedAt,
        Instant createdAt
) {

    public static PrescriptionOrder create(UUID encounterId, UUID patientId, UUID facilityId, UUID prescribedBy) {
        return new PrescriptionOrder(null, encounterId, patientId, facilityId, prescribedBy, null, null);
    }

    /** Pre-generated id, so the charge posted before insert (sourceEventId) can point at this order — LabOrder.withId's pattern. */
    public PrescriptionOrder withId(UUID id) {
        return new PrescriptionOrder(id, encounterId, patientId, facilityId, prescribedBy, prescribedAt, createdAt);
    }
}
