package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 17-ward-nurse.md §8.2 ⚠: care responsibility comes from this roster, not
// a single assignedNurse field that cannot survive a multi-day stay across
// three shifts a day. "Currently responsible nurse" is derived by reading
// this roster for (admission, today, current shift) — never stored.
public record NursingAssignment(
        UUID id,
        UUID admissionId,
        UUID nurseId,
        Shift shift,
        LocalDate assignmentDate,
        UUID assignedBy,
        Instant createdAt
) {
}
