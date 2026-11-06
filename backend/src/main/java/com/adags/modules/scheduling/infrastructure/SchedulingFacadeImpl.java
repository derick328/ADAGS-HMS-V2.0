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
    private final com.adags.modules.scheduling.application.ClinicFollowUpService clinicFollowUpService;

    public SchedulingFacadeImpl(AppointmentRepository appointmentRepository,
                                com.adags.modules.scheduling.application.ClinicFollowUpService clinicFollowUpService) {
        this.appointmentRepository = appointmentRepository;
        this.clinicFollowUpService = clinicFollowUpService;
    }

    @Override
    public void validateCheckIn(UUID appointmentId, UUID patientId, UUID departmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found: " + appointmentId));
        if (!appointment.patientId().equals(patientId) || (appointment.status() != AppointmentStatus.SCHEDULED && appointment.status() != AppointmentStatus.NO_SHOW)) {
            throw new IllegalArgumentException("Appointment is not scheduled for this patient.");
        }
        // Clinic appointments must use their registered department.
        if (appointment.clinicFollowUpId() != null && !clinicDepartment(appointment.clinicFollowUpId()).equals(departmentId)) {
            throw new IllegalArgumentException("Select the appointment's clinic department.");
        }
    }

    private UUID clinicDepartment(UUID planId) {
        return clinicFollowUpService.plan(planId).departmentId();
    }

    @Override
    public void markAttended(UUID appointmentId, UUID encounterId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NoSuchElementException("Appointment not found: " + appointmentId));
        if ((appointment.status() != AppointmentStatus.SCHEDULED && appointment.status() != AppointmentStatus.NO_SHOW)) {
            // Already resolved (a retried check-in hitting this twice, or a
            // stale client) — never re-marks a CANCELLED/NO_SHOW row or
            // overwrites which encounter an already-ATTENDED one became.
            return;
        }
        appointmentRepository.markAttended(appointmentId, encounterId);
    }
}
