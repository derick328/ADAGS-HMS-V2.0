package com.adags.modules.clinical.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 06-triage.md §2.2/§2.3. weightKg/heightCm are mandatory per the field set;
// bmi is DB-generated (V10 migration: GENERATED ALWAYS AS ... STORED) and
// therefore never settable here on insert — it is only ever a value read
// back from a persisted row, hence nullable (null for an unpersisted
// instance). avpu and bloodGlucoseMmol are genuinely optional (§7.3, §7.4).
public record VitalSigns(
        UUID id,
        UUID encounterId,
        VitalSignsContext context,
        UUID contextRefId,
        List<RecordedVital> readings,
        double weightKg,
        double heightCm,
        Double bmi,
        Avpu avpu,
        Double bloodGlucoseMmol,
        UUID recordedBy,
        Instant recordedAt,
        Instant occurredAt
) {

    public RecordedVital reading(VitalType vitalType) {
        return readings.stream()
                .filter(r -> r.vitalType() == vitalType)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No recorded reading for " + vitalType));
    }
}
