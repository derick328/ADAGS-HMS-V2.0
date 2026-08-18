package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.UUID;

// 07-doctor.md §7 (ADMITTED disposition) + §11, 04-procedure-bedday-rules.md
// §3.6. One row per encounter (idx_admission_one_per_encounter) — the
// encounter's own state (ADMITTED -> IN_WARD -> DISCHARGE_ORDERED ->
// DISCHARGED) is the source of truth for where the admission stands;
// this record carries the inpatient-specific facts that state machine
// doesn't: who ordered it, into what class, and the two discharge
// timestamps.
//
// discharge_ordered_at/By/Role and actual_departure_at are null until
// ordered (step 3, discharge). actual_departure_at is this admission's own
// field, not any one BedAssignment.released_at — a transfer releases an
// earlier bed well before the patient actually leaves the building.
public record Admission(
        UUID id,
        UUID encounterId,
        UUID patientId,
        UUID facilityId,
        UUID admittingDoctorId,
        WardClass orderedWardClass,
        Instant admittedAt,
        Instant dischargeOrderedAt,
        UUID dischargeOrderedBy,
        /** Not an authorization check — a reporting field distinguishing doctor-ordered from nurse-ordered discharges. */
        DischargeOrdererRole dischargeOrdererRole,
        Instant actualDepartureAt,
        int version,
        Instant createdAt
) {
}
