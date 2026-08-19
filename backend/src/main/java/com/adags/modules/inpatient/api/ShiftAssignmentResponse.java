package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.ShiftAssignment;
import com.adags.modules.inpatient.domain.ShiftAssignmentStatus;
import com.adags.modules.inpatient.domain.ShiftRoleContext;

import java.time.LocalDate;
import java.util.UUID;

public record ShiftAssignmentResponse(
        UUID id, UUID userId, UUID workShiftId, LocalDate shiftDate, UUID wardId, UUID departmentId,
        ShiftRoleContext roleContext, ShiftAssignmentStatus status
) {
    public static ShiftAssignmentResponse from(ShiftAssignment assignment) {
        return new ShiftAssignmentResponse(assignment.id(), assignment.userId(), assignment.workShiftId(),
                assignment.shiftDate(), assignment.wardId(), assignment.departmentId(), assignment.roleContext(),
                assignment.status());
    }
}
