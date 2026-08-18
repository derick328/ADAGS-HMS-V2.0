package com.adags.modules.encounters.application;

import java.util.UUID;

// 05-receptionist.md §5.2: "send to triage" — check-in opens an Encounter,
// it does not create an appointment (⚠7).
public record OpenEncounterCommand(
        UUID patientId,
        UUID departmentId,
        /** Non-null only when the patient arrived against a booked appointment — ⚠7. */
        UUID appointmentId,
        UUID actorUserId
) {
}
