package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.Shift;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 17-ward-nurse.md §11: "My History" — one row per roster assignment this
// nurse has held, joined with enough of the admission to identify the
// patient without a second round trip per row.
public record NursingAssignmentHistoryRowResponse(
        UUID id,
        UUID admissionId,
        UUID encounterId,
        UUID patientId,
        String patientName,
        String hospitalNumber,
        Shift shift,
        LocalDate assignmentDate,
        Instant createdAt
) {
}
