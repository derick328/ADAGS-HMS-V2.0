package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.ShiftRoleContext;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AssignShiftRequest(
        @NotNull UUID userId,
        @NotNull UUID workShiftId,
        @NotNull LocalDate shiftDate,
        UUID wardId,
        @NotNull UUID departmentId,
        @NotNull ShiftRoleContext roleContext
) {
}
