package com.adags.modules.inpatient.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

public record DefineWorkShiftRequest(
        @NotBlank String code,
        @NotBlank String name,
        UUID departmentId,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        boolean crossesMidnight,
        int daysOfWeek
) {
}
