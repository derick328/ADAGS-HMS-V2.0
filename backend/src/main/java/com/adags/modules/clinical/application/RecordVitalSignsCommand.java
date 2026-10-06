package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.Avpu;
import com.adags.modules.clinical.domain.VitalSignsContext;

import java.time.Instant;
import java.util.UUID;

// Raw, unevaluated input for RecordVitalSignsService. All readings are
// nullable Double: triage sends the full mandatory set (06-triage.md §2.2,
// enforced by its request DTO), but a CONSULTATION re-measure may record only
// some (07-doctor.md §5.1) — the service evaluates and persists exactly the
// ones provided. avpu and bloodGlucoseMmol stay nullable (§7.3/§7.4).
public record RecordVitalSignsCommand(
        UUID encounterId,
        UUID patientId,
        VitalSignsContext context,
        UUID contextRefId,
        Double temperatureC,
        Double systolicMmHg,
        Double diastolicMmHg,
        Double pulseBpm,
        Double respiratoryRate,
        Double spo2Pct,
        Double weightKg,
        Double heightCm,
        Avpu avpu,
        Double bloodGlucoseMmol,
        Integer painScore,
        Integer intakeMl,
        Integer outputMl,
        UUID recordedBy,
        Instant occurredAt
) {
}
