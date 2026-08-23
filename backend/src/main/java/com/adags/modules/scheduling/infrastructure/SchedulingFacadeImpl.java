package com.adags.modules.scheduling.infrastructure;

import com.adags.modules.scheduling.SchedulingFacade;
import com.adags.modules.scheduling.domain.Appointment;
import com.adags.modules.scheduling.domain.AppointmentRepository;
import com.adags.modules.scheduling.domain.AppointmentStatus;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.UUID;

@Component
public class SchedulingFacadeImpl implements SchedulingFacade {

    private final AppointmentRepository appointmentRepository;

    public SchedulingFacadeImpl(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public void markAttended(UUID appointmentId, UUID encounterId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found: " + appointmentId));
        if (appointment.status() != AppointmentStatus.SCHEDULED) {
            // Already resolved (a retried check-in hitting this twice, or a
            // stale client) — never re-marks a CANCELLED/NO_SHOW row or
            // overwrites which encounter an already-ATTENDED one became.
            return;
        }
        appointmentRepository.markAttended(appointmentId, encounterId);
    }
}
