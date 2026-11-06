package com.adags.modules.scheduling.application;

import com.adags.modules.scheduling.domain.Appointment;
import com.adags.modules.scheduling.domain.AppointmentRepository;
import com.adags.modules.scheduling.domain.AppointmentStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ClinicFollowUpService {
    public record Plan(UUID id, UUID patientId, UUID facilityId, UUID departmentId, UUID providerId,
                       String clinic, String doctor, boolean active) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public ClinicFollowUpService(NamedParameterJdbcTemplate jdbc, AppointmentRepository appointments, Clock clock) {
        this.jdbc = jdbc;
        this.appointments = appointments;
        this.clock = clock;
    }

    public List<Plan> plansForProvider(UUID providerId) {
        return queryPlans(" WHERE c.provider_id = :providerId", new MapSqlParameterSource("providerId", providerId));
    }

    public List<Plan> plans(UUID patientId, UUID facilityId) {
        String where = patientId != null ? " WHERE c.patient_id = :patientId" : " WHERE c.facility_id = :facilityId";
        return queryPlans(where, new MapSqlParameterSource().addValue("patientId", patientId).addValue("facilityId", facilityId));
    }

    private List<Plan> queryPlans(String where, MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT c.id, c.patient_id, c.facility_id, c.department_id, c.provider_id,
                       c.active, c.clinic_name AS clinic, u.username AS doctor
                FROM clinic_follow_up c JOIN department d ON d.id = c.department_id
                JOIN app_user u ON u.id = c.provider_id
                """ + where + " ORDER BY c.active DESC, c.created_at DESC",
                params,
                (rs, row) -> new Plan((UUID) rs.getObject("id"), (UUID) rs.getObject("patient_id"),
                        (UUID) rs.getObject("facility_id"), (UUID) rs.getObject("department_id"),
                        (UUID) rs.getObject("provider_id"), rs.getString("clinic"),
                        rs.getString("doctor"), rs.getBoolean("active")));
    }

    public String providerName(UUID providerId) {
        return jdbc.queryForObject("SELECT username FROM app_user WHERE id = :id",
                new MapSqlParameterSource("id", providerId), String.class);
    }

    public UUID sourceDepartment(UUID encounterId) {
        return jdbc.queryForObject("SELECT department_id FROM encounter WHERE id = :id",
                new MapSqlParameterSource("id", encounterId), UUID.class);
    }

    public Plan plan(UUID id) {
        return jdbc.query("""
                SELECT c.id, c.patient_id, c.facility_id, c.department_id, c.provider_id,
                       c.active, c.clinic_name AS clinic, u.username AS doctor
                FROM clinic_follow_up c JOIN department d ON d.id = c.department_id
                JOIN app_user u ON u.id = c.provider_id WHERE c.id = :id
                """, new MapSqlParameterSource("id", id), (rs, row) ->
                new Plan((UUID) rs.getObject("id"), (UUID) rs.getObject("patient_id"),
                        (UUID) rs.getObject("facility_id"), (UUID) rs.getObject("department_id"),
                        (UUID) rs.getObject("provider_id"), rs.getString("clinic"),
                        rs.getString("doctor"), rs.getBoolean("active"))).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Clinic plan not found"));
    }

    @Transactional
    public Appointment schedule(UUID patientId, UUID encounterId, UUID departmentId, String clinicName,
                                Instant scheduledAt, String instructions, UUID doctorId) {
        if (scheduledAt == null || !scheduledAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Choose a future clinic return date.");
        }
        if (clinicName == null || clinicName.isBlank()) throw new IllegalArgumentException("Choose a clinic program.");
        String normalizedClinic = clinicName.trim();
        var params = new MapSqlParameterSource().addValue("clinicName", normalizedClinic).addValue("patientId", patientId)
                .addValue("encounterId", encounterId).addValue("departmentId", departmentId)
                .addValue("doctorId", doctorId);
        Integer valid = jdbc.queryForObject("""
                SELECT count(*) FROM encounter e JOIN department d ON d.id = :departmentId
                WHERE e.id = :encounterId AND e.patient_id = :patientId AND e.facility_id = d.facility_id AND e.assigned_provider_id = :doctorId
                """, params, Integer.class);
        if (valid == null || valid != 1) throw new IllegalArgumentException("Encounter and clinic must belong to the same patient and facility.");
        UUID planId = jdbc.query("""
                SELECT id FROM clinic_follow_up WHERE patient_id = :patientId AND department_id = :departmentId
                AND lower(clinic_name) = lower(:clinicName) AND active
                """, params, (rs, row) -> (UUID) rs.getObject(1)).stream().findFirst().orElse(null);
        if (planId == null) {
            planId = jdbc.queryForObject("""
                    INSERT INTO clinic_follow_up(patient_id, facility_id, department_id, clinic_name, provider_id)
                    SELECT :patientId, facility_id, :departmentId, :clinicName, :doctorId FROM department WHERE id = :departmentId
                    RETURNING id
                    """, params, UUID.class);
        }
        if (appointments.findByClinicFollowUpId(planId).stream().anyMatch(a -> a.status() == AppointmentStatus.SCHEDULED)) {
            throw new IllegalArgumentException("This clinic already has a scheduled return. Reschedule it instead.");
        }
        jdbc.update("UPDATE clinic_follow_up SET provider_id = :doctorId WHERE id = :planId",
                params.addValue("planId", planId));
        return appointments.insert(new Appointment(null, patientId, doctorId, encounterId, scheduledAt,
                AppointmentStatus.SCHEDULED, null, instructions, null, doctorId, planId, null, null));
    }

    @Transactional
    public Appointment reschedule(UUID appointmentId, Instant scheduledAt, String instructions) {
        Appointment appointment = appointments.findById(appointmentId).orElseThrow();
        if (appointment.clinicFollowUpId() == null || appointment.status() != AppointmentStatus.SCHEDULED
                || scheduledAt == null || !scheduledAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Only a scheduled clinic appointment can be rescheduled to a future date.");
        }
        appointments.reschedule(appointmentId, scheduledAt, instructions);
        return appointments.findById(appointmentId).orElseThrow();
    }

    @Transactional
    public void close(UUID planId) {
        Plan plan = plan(planId);
        if (!plan.active()) return;
        for (Appointment appointment : appointments.findByClinicFollowUpId(planId)) {
            if (appointment.status() == AppointmentStatus.SCHEDULED) appointments.setStatus(appointment.id(), AppointmentStatus.CANCELLED);
        }
        jdbc.update("UPDATE clinic_follow_up SET active = false, closed_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", planId));
    }
}
