package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.Avpu;
import com.adags.modules.clinical.domain.VitalSignsContext;

import java.time.Instant;
import java.util.UUID;

// Raw, unevaluated input for RecordVitalSignsService — the six core vitals
// are primitives here because every one of them is mandatory (06-triage.md
// §2.2); avpu and bloodGlucoseMmol stay nullable (§7.3/§7.4).
public record RecordVitalSignsCommand(
        UUID encounterId,
        UUID patientId,
        VitalSignsContext context,
        UUID contextRefId,
        double temperatureC,
        double systolicMmHg,
        double diastolicMmHg,
        double pulseBpm,
        double respiratoryRate,
        double spo2Pct,
        double weightKg,
        double heightCm,
        Avpu avpu,
        Double bloodGlucoseMmol,
        Integer painScore,
        Integer intakeMl,
        Integer outputMl,
        UUID recordedBy,
        Instant occurredAt
) {
}
