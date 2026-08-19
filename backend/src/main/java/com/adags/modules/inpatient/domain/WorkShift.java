package com.adags.modules.inpatient.domain;

import java.time.LocalTime;
import java.util.UUID;

// 19-work-shifts-roster.md §2. No `scheduling` module exists yet in this
// codebase — self-contained here inside `inpatient` for now, the same
// treatment prescription_order/prescription_line got inside `pharmacy`
// when no `orders` module existed (V25's own comment documents the
// precedent). crossesMidnight is an explicit, stored fact, never inferred
// from endTime.isBefore(startTime) — §2's own ⚠: every "is this nurse on
// duty now" calculation breaks silently on a night shift without it.
// daysOfWeek is a 7-bit mask, Monday = bit 0 .. Sunday = bit 6.
public record WorkShift(
        UUID id,
        String code,
        String name,
        UUID departmentId,
        LocalTime startTime,
        LocalTime endTime,
        boolean crossesMidnight,
        int daysOfWeek,
        boolean isActive,
        UUID createdBy,
        java.time.Instant createdAt
) {

    public static WorkShift define(String code, String name, UUID departmentId, LocalTime startTime,
                                    LocalTime endTime, boolean crossesMidnight, int daysOfWeek, UUID createdBy) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required.");
        }
        if (daysOfWeek < 1 || daysOfWeek > 127) {
            throw new IllegalArgumentException("daysOfWeek must be a non-empty 7-bit mask (1-127).");
        }
        return new WorkShift(null, code, name, departmentId, startTime, endTime, crossesMidnight, daysOfWeek,
                true, createdBy, null);
    }
}
