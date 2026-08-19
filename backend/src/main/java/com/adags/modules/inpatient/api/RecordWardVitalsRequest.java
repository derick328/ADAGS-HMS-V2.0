package com.adags.modules.inpatient.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RecordWardVitalsRequest(
        @NotNull Double temperatureC,
        @NotNull Double systolicMmHg,
        @NotNull Double diastolicMmHg,
        @NotNull Double pulseBpm,
        @NotNull Double respiratoryRate,
        @NotNull Double spo2Pct,
        @NotNull Double weightKg,
        @NotNull Double heightCm,
        String avpu,
        Double bloodGlucoseMmol,
        Integer painScore,
        Integer intakeMl,
        Integer outputMl,
        @NotNull Instant occurredAt
) {
}
