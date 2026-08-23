package com.adags.modules.surgery.infrastructure;

import com.adags.modules.surgery.domain.ChecklistPhase;
import com.adags.modules.surgery.domain.SurgicalChecklist;
import com.adags.modules.surgery.domain.SurgicalChecklistRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcSurgicalChecklistRepository implements SurgicalChecklistRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, surgery_order_id, phase, items::text AS items, completed_by, completed_at,
                   count_correct, count_discrepancy_note, is_emergency_reduced
            FROM surgical_checklist
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSurgicalChecklistRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public SurgicalChecklist insert(SurgicalChecklist checklist) {
        String sql = """
                INSERT INTO surgical_checklist (
                    surgery_order_id, phase, items, completed_by, count_correct, count_discrepancy_note,
                    is_emergency_reduced)
                VALUES (
                    :surgeryOrderId, :phase, CAST(:items AS JSONB), :completedBy, :countCorrect,
                    :countDiscrepancyNote, :isEmergencyReduced)
                RETURNING id, completed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("surgeryOrderId", checklist.surgeryOrderId())
                .addValue("phase", checklist.phase().name())
                .addValue("items", writeJson(checklist.items()))
                .addValue("completedBy", checklist.completedBy())
                .addValue("countCorrect", checklist.countCorrect(), Types.BOOLEAN)
                .addValue("countDiscrepancyNote", checklist.countDiscrepancyNote())
                .addValue("isEmergencyReduced", checklist.isEmergencyReduced());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new SurgicalChecklist(
                (UUID) rs.getObject("id"), checklist.surgeryOrderId(), checklist.phase(), checklist.items(),
                checklist.completedBy(), rs.getTimestamp("completed_at").toInstant(), checklist.countCorrect(),
                checklist.countDiscrepancyNote(), checklist.isEmergencyReduced()));
    }

    @Override
    public List<SurgicalChecklist> findBySurgeryOrderId(UUID surgeryOrderId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE surgery_order_id = :surgeryOrderId ORDER BY completed_at",
                new MapSqlParameterSource("surgeryOrderId", surgeryOrderId), this::mapRow);
    }

    @Override
    public boolean isPhaseSatisfied(UUID surgeryOrderId, ChecklistPhase phase) {
        String condition = phase == ChecklistPhase.SIGN_OUT ? "AND count_correct = TRUE" : "";
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surgical_checklist WHERE surgery_order_id = :surgeryOrderId AND phase = :phase "
                        + condition,
                new MapSqlParameterSource()
                        .addValue("surgeryOrderId", surgeryOrderId)
                        .addValue("phase", phase.name()),
                Integer.class);
        return count != null && count > 0;
    }

    private String writeJson(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize checklist items", e);
        }
    }

    private List<String> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize checklist items", e);
        }
    }

    private SurgicalChecklist mapRow(ResultSet rs, int rowNum) throws SQLException {
        boolean countCorrectNull = rs.getObject("count_correct") == null;
        return new SurgicalChecklist(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("surgery_order_id"),
                ChecklistPhase.valueOf(rs.getString("phase")),
                readJson(rs.getString("items")),
                (UUID) rs.getObject("completed_by"),
                rs.getTimestamp("completed_at").toInstant(),
                countCorrectNull ? null : rs.getBoolean("count_correct"),
                rs.getString("count_discrepancy_note"),
                rs.getBoolean("is_emergency_reduced"));
    }
}
