package com.adags.modules.scheduling.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository {

    Appointment insert(Appointment appointment);

    Optional<Appointment> findById(UUID id);

    /** Scheduled (any status) with scheduled_at in [from, to) — the dashboard's "today"/"upcoming" views. */
    List<Appointment> findScheduledBetween(Instant from, Instant to);

    /** Resolved or past appointments before {@code before}, most recent first — the dashboard's "8 most recent". */
    List<Appointment> findRecentPast(Instant before, int limit);

    /**
     * 05a-receptionist-decisions.md §3.2: still SCHEDULED, due within the
     * next 48 hours or already overdue — no upper bound, since a row stays
     * listed until the appointment resolves.
     */
    List<Appointment> findExpectedReturns(Instant windowEnd);

    /** SCHEDULED -> ATTENDED, and records which new encounter it turned into (§3.3). */
    void markAttended(UUID id, UUID encounterId);

    List<Appointment> findByPatientId(UUID patientId);
    List<Appointment> findByClinicFollowUpId(UUID clinicFollowUpId);
    List<Appointment> findActiveClinicAppointments();
    void reschedule(UUID id, Instant scheduledAt, String notes);
    void setStatus(UUID id, AppointmentStatus status);
}
