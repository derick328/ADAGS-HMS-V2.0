package com.adags.modules.encounters.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenEncounterRequest(
        @NotNull UUID patientId,
        UUID departmentId,
        /** Non-null only when the patient arrived against a booked appointment — 05-receptionist.md ⚠7. */
        UUID appointmentId,
        UUID facilityId
) {
}
