package com.adags.modules.surgery.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// 17-ward-nurse.md §10: post-op observations — same field set as ward
// rounds' RecordWardVitalsRequest, minus painScore/intakeMl/outputMl (the
// ward-specific fluid-balance additions, not asked for here).
public record RecordSurgeryVitalsRequest(
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
        @NotNull Instant occurredAt
) {
}
