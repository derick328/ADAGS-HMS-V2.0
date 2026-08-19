package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.ShiftHandover;
import com.adags.modules.inpatient.domain.ShiftHandoverRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcShiftHandoverRepository implements ShiftHandoverRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, admission_id, ward_id, shift_from, shift_to, handover_at, outgoing_nurse_id,
                   incoming_nurse_id, acknowledged_at, acknowledged_by, situation, background, assessment,
                   recommendation, outstanding_tasks::text AS outstanding_tasks, created_at
            FROM shift_handover
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcShiftHandoverRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ShiftHandover insert(ShiftHandover handover) {
        String sql = """
                INSERT INTO shift_handover (admission_id, ward_id, shift_from, shift_to, outgoing_nurse_id,
                                             incoming_nurse_id, situation, background, assessment, recommendation,
                                             outstanding_tasks)
                VALUES (:admissionId, :wardId, :shiftFrom, :shiftTo, :outgoingNurseId,
                        :incomingNurseId, :situation, :background, :assessment, :recommendation,
                        CAST(:outstandingTasks AS JSONB))
                RETURNING id, handover_at, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("admissionId", handover.admissionId())
                .addValue("wardId", handover.wardId())
                .addValue("shiftFrom", handover.shiftFrom())
                .addValue("shiftTo", handover.shiftTo(), Types.OTHER)
                .addValue("outgoingNurseId", handover.outgoingNurseId())
                .addValue("incomingNurseId", handover.incomingNurseId(), Types.OTHER)
                .addValue("situation", handover.situation())
                .addValue("background", handover.background())
                .addValue("assessment", handover.assessment())
                .addValue("recommendation", handover.recommendation())
                .addValue("outstandingTasks", writeJson(handover.outstandingTasks()));

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ShiftHandover(
                (UUID) rs.getObject("id"), handover.admissionId(), handover.wardId(), handover.shiftFrom(),
                handover.shiftTo(), rs.getTimestamp("handover_at").toInstant(), handover.outgoingNurseId(),
                handover.incomingNurseId(), null, null, handover.situation(), handover.background(),
                handover.assessment(), handover.recommendation(), handover.outstandingTasks(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<ShiftHandover> findById(UUID id) {
        List<ShiftHandover> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<ShiftHandover> findByAdmissionId(UUID admissionId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE admission_id = :admissionId ORDER BY handover_at DESC",
                new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
    }

    @Override
    public boolean acknowledge(UUID handoverId, UUID acknowledgedBy) {
        String sql = "UPDATE shift_handover SET acknowledged_at = now(), acknowledged_by = :acknowledgedBy "
                + "WHERE id = :id AND acknowledged_at IS NULL";
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("acknowledgedBy", acknowledgedBy)
                .addValue("id", handoverId));
        return updated > 0;
    }

    private String writeJson(List<String> tasks) {
        try {
            return objectMapper.writeValueAsString(tasks == null ? List.of() : tasks);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outstanding_tasks", e);
        }
    }

    private List<String> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize outstanding_tasks", e);
        }
    }

    private ShiftHandover mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp acknowledgedAt = rs.getTimestamp("acknowledged_at");
        return new ShiftHandover(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("admission_id"),
                (UUID) rs.getObject("ward_id"),
                (UUID) rs.getObject("shift_from"),
                (UUID) rs.getObject("shift_to"),
                rs.getTimestamp("handover_at").toInstant(),
                (UUID) rs.getObject("outgoing_nurse_id"),
                (UUID) rs.getObject("incoming_nurse_id"),
                acknowledgedAt == null ? null : acknowledgedAt.toInstant(),
                (UUID) rs.getObject("acknowledged_by"),
                rs.getString("situation"),
                rs.getString("background"),
                rs.getString("assessment"),
                rs.getString("recommendation"),
                readJson(rs.getString("outstanding_tasks")),
                rs.getTimestamp("created_at").toInstant());
    }
}
