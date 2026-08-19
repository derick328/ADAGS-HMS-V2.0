package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.ShiftRoleContext;

import java.time.LocalDate;
import java.util.UUID;

public record AssignShiftCommand(
        UUID userId, UUID workShiftId, LocalDate shiftDate, UUID wardId, UUID departmentId,
        ShiftRoleContext roleContext, UUID assignedBy
) {
}
