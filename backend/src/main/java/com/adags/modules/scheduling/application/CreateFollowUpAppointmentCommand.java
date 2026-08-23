package com.adags.modules.scheduling.application;

import java.time.Instant;
import java.util.UUID;

public record CreateFollowUpAppointmentCommand(
        UUID patientId,
        UUID sourceEncounterId,
        Instant scheduledAt,
        String notes,
        UUID actorUserId
) {
}
