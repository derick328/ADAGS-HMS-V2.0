package com.adags.modules.scheduling.domain;

import java.time.Instant;
import java.util.UUID;

// 05a-receptionist-decisions.md §3: the only appointment in v1 is a
// doctor-booked follow-up (D1 — forward booking out of scope). No
// appointment_type column: nothing else creates one yet, so a type
// enumeration with one legal value would just be ceremony.
public record Appointment(
        UUID id,
        UUID patientId,
        UUID providerId,
        UUID sourceEncounterId,
        Instant scheduledAt,
        AppointmentStatus status,
        /** Set on arrival (§3.3) — the new encounter this follow-up turned into. Null until then. */
        UUID attendedEncounterId,
        String notes,
        Instant createdAt,
        UUID createdBy
) {

    public static Appointment scheduleFollowUp(UUID patientId, UUID providerId, UUID sourceEncounterId,
                                                Instant scheduledAt, String notes, UUID createdBy) {
        return new Appointment(null, patientId, providerId, sourceEncounterId, scheduledAt,
                AppointmentStatus.SCHEDULED, null, notes, null, createdBy);
    }
}
