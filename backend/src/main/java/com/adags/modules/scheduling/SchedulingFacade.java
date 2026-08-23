package com.adags.modules.scheduling;

import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). First caller is encounters: 05a-receptionist-decisions.md
// §3.3 — checking in against a follow-up appointment marks it ATTENDED and
// records which encounter it became, in the same transaction as opening
// that encounter.
public interface SchedulingFacade {

    /**
     * SCHEDULED -> ATTENDED. A no-op if the appointment is already ATTENDED
     * with this same encounterId (idempotent against a retried check-in) —
     * never re-marks a CANCELLED or NO_SHOW appointment.
     */
    void markAttended(UUID appointmentId, UUID encounterId);
}
