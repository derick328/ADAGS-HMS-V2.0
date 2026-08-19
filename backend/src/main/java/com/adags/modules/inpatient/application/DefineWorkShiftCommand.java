package com.adags.modules.inpatient.application;

import java.time.LocalTime;
import java.util.UUID;

public record DefineWorkShiftCommand(
        String code, String name, UUID departmentId, LocalTime startTime, LocalTime endTime,
        boolean crossesMidnight, int daysOfWeek, UUID createdBy
) {
}
