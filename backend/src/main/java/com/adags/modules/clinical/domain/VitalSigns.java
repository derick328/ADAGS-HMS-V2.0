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
        // 17-ward-nurse.md §4.1/§4.4: pain_score is in the spec's full field
        // list; intake_ml/output_ml (fluid balance) are called out
        // explicitly as absent and expected day one. All nullable —
        // triage/consultation/post_op readings don't carry fluid balance,
        // and every row before this column existed has none.
        Integer painScore,
        Integer intakeMl,
        Integer outputMl,
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
