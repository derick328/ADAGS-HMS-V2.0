package com.adags.modules.scheduling.application;

import com.adags.modules.scheduling.domain.Appointment;
import com.adags.modules.scheduling.domain.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// 05a-receptionist-decisions.md §3/D1: the only way an appointment comes
// into existence in v1 — a doctor booking a follow-up against the
// consultation they just finished. The provider is always the booking
// doctor themselves; there is no provider-selection step to book someone
// else's follow-up.
@Service
public class CreateFollowUpAppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public CreateFollowUpAppointmentService(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional
    public Appointment create(CreateFollowUpAppointmentCommand command) {
        if (command.scheduledAt() == null || !command.scheduledAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("Enter a follow-up date in the future.");
        }
        Appointment appointment = Appointment.scheduleFollowUp(command.patientId(), command.actorUserId(),
                command.sourceEncounterId(), command.scheduledAt(), command.notes(), command.actorUserId());
        return appointmentRepository.insert(appointment);
    }
}
