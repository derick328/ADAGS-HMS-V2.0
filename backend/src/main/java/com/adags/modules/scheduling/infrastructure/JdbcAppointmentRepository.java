package com.adags.modules.scheduling.infrastructure;

import com.adags.modules.scheduling.domain.Appointment;
import com.adags.modules.scheduling.domain.AppointmentRepository;
import com.adags.modules.scheduling.domain.AppointmentStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAppointmentRepository implements AppointmentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, patient_id, provider_id, source_encounter_id, scheduled_at, status,
                   attended_encounter_id, notes, created_at, created_by
            FROM appointment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAppointmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Appointment insert(Appointment appointment) {
        String sql = """
                INSERT INTO appointment (patient_id, provider_id, source_encounter_id, scheduled_at, notes, created_by)
                VALUES (:patientId, :providerId, :sourceEncounterId, :scheduledAt, :notes, :createdBy)
                RETURNING id, status, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("patientId", appointment.patientId())
                .addValue("providerId", appointment.providerId())
                .addValue("sourceEncounterId", appointment.sourceEncounterId())
                .addValue("scheduledAt", Timestamp.from(appointment.scheduledAt()))
                .addValue("notes", appointment.notes(), Types.VARCHAR)
                .addValue("createdBy", appointment.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Appointment(
                (UUID) rs.getObject("id"), appointment.patientId(), appointment.providerId(),
                appointment.sourceEncounterId(), appointment.scheduledAt(),
                AppointmentStatus.valueOf(rs.getString("status")), null, appointment.notes(),
                rs.getTimestamp("created_at").toInstant(), appointment.createdBy()));
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        List<Appointment> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Appointment> findScheduledBetween(Instant from, Instant to) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE scheduled_at >= :from AND scheduled_at < :to ORDER BY scheduled_at",
                new MapSqlParameterSource()
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to)),
                this::mapRow);
    }

    @Override
    public List<Appointment> findRecentPast(Instant before, int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE scheduled_at < :before ORDER BY scheduled_at DESC LIMIT :limit",
                new MapSqlParameterSource().addValue("before", Timestamp.from(before)).addValue("limit", limit),
                this::mapRow);
    }

    @Override
    public List<Appointment> findExpectedReturns(Instant windowEnd) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = 'SCHEDULED' AND scheduled_at <= :windowEnd ORDER BY scheduled_at",
                new MapSqlParameterSource("windowEnd", Timestamp.from(windowEnd)),
                this::mapRow);
    }

    @Override
    public void markAttended(UUID id, UUID encounterId) {
        jdbcTemplate.update(
                "UPDATE appointment SET status = 'ATTENDED', attended_encounter_id = :encounterId WHERE id = :id",
                new MapSqlParameterSource().addValue("encounterId", encounterId).addValue("id", id));
    }

    private Appointment mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID attendedEncounterId = (UUID) rs.getObject("attended_encounter_id");
        return new Appointment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("provider_id"),
                (UUID) rs.getObject("source_encounter_id"),
                rs.getTimestamp("scheduled_at").toInstant(),
                AppointmentStatus.valueOf(rs.getString("status")),
                attendedEncounterId,
                rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant(),
                (UUID) rs.getObject("created_by"));
    }
}
