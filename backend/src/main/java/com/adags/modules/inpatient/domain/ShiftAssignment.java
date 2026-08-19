package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 19-work-shifts-roster.md §3. shiftDate anchors a midnight-crossing shift
// to its start date — the night shift of 12 July runs 12 July 23:00 to
// 13 July 07:00 and is filed under 12 July, never split across two rows.
// The roster's own record of "who is on duty at all," keyed by ward or
// department — distinct from NursingAssignment (V31), which answers "who
// is responsible for THIS admission this shift." Both stay, side by side.
public record ShiftAssignment(
        UUID id,
        UUID userId,
        UUID workShiftId,
        LocalDate shiftDate,
        UUID wardId,
        UUID departmentId,
        ShiftRoleContext roleContext,
        ShiftAssignmentStatus status,
        UUID assignedBy,
        Instant assignedAt,
        UUID swappedWithUserId,
        String swapReason
) {

    public static ShiftAssignment assign(UUID userId, UUID workShiftId, LocalDate shiftDate, UUID wardId,
                                          UUID departmentId, ShiftRoleContext roleContext, UUID assignedBy) {
        return new ShiftAssignment(null, userId, workShiftId, shiftDate, wardId, departmentId, roleContext,
                ShiftAssignmentStatus.SCHEDULED, assignedBy, null, null, null);
    }
}
