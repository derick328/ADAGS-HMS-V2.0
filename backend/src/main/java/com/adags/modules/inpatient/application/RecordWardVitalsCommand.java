package com.adags.modules.inpatient.application;

import java.time.Instant;
import java.util.UUID;

public record RecordWardVitalsCommand(
        UUID admissionId,
        double temperatureC,
        double systolicMmHg,
        double diastolicMmHg,
        double pulseBpm,
        double respiratoryRate,
        double spo2Pct,
        double weightKg,
        double heightCm,
        String avpu,
        Double bloodGlucoseMmol,
        Integer painScore,
        Integer intakeMl,
        Integer outputMl,
        UUID recordedBy,
        Instant occurredAt
) {
}
