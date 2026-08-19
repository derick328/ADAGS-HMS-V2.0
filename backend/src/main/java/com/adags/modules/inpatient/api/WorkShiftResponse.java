package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.WorkShift;

import java.time.LocalTime;
import java.util.UUID;

public record WorkShiftResponse(
        UUID id, String code, String name, UUID departmentId, LocalTime startTime, LocalTime endTime,
        boolean crossesMidnight, int daysOfWeek, boolean isActive
) {
    public static WorkShiftResponse from(WorkShift workShift) {
        return new WorkShiftResponse(workShift.id(), workShift.code(), workShift.name(), workShift.departmentId(),
                workShift.startTime(), workShift.endTime(), workShift.crossesMidnight(), workShift.daysOfWeek(),
                workShift.isActive());
    }
}
