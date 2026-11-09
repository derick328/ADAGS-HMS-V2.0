package com.adags.modules.clinical.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPregnancyAssessmentRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPregnancyAssessmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> latestStatus(UUID encounterId) {
        List<String> values = jdbc.query("""
                SELECT status FROM consultation_pregnancy_assessment
                WHERE encounter_id = :encounterId
                ORDER BY recorded_at DESC, id DESC LIMIT 1
                """, new MapSqlParameterSource("encounterId", encounterId),
                (rs, rowNum) -> rs.getString("status"));
        return values.stream().findFirst();
    }

    public void append(UUID encounterId, String status, UUID actorId) {
        jdbc.update("""
                INSERT INTO consultation_pregnancy_assessment (encounter_id, status, recorded_by)
                VALUES (:encounterId, :status, :actorId)
                """, new MapSqlParameterSource()
                .addValue("encounterId", encounterId)
                .addValue("status", status)
                .addValue("actorId", actorId));
    }
}
