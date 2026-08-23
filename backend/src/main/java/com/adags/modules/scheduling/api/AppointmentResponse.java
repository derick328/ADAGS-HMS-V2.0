package com.adags.modules.scheduling.api;

import com.adags.modules.scheduling.domain.Appointment;
import com.adags.modules.scheduling.domain.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        UUID providerId,
        UUID sourceEncounterId,
        Instant scheduledAt,
        AppointmentStatus status,
        UUID attendedEncounterId,
        String notes,
        Instant createdAt
) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.id(), appointment.patientId(), appointment.providerId(),
                appointment.sourceEncounterId(), appointment.scheduledAt(), appointment.status(),
                appointment.attendedEncounterId(), appointment.notes(), appointment.createdAt());
    }
}
