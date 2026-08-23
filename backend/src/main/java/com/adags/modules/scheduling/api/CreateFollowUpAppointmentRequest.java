package com.adags.modules.scheduling.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateFollowUpAppointmentRequest(
        @NotNull UUID patientId,
        @NotNull UUID sourceEncounterId,
        @NotNull Instant scheduledAt,
        String notes
) {
}
