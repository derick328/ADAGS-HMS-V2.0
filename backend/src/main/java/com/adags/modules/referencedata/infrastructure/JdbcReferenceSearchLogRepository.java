package com.adags.modules.referencedata.infrastructure;

import com.adags.modules.referencedata.domain.ReferenceSearchLog;
import com.adags.modules.referencedata.domain.ReferenceSearchLogRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.UUID;

@Repository
public class JdbcReferenceSearchLogRepository implements ReferenceSearchLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcReferenceSearchLogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReferenceSearchLog insert(ReferenceSearchLog log) {
        String sql = """
                INSERT INTO reference_search_log (actor_user_id, department_id, catalogue_type, query_text, result_count)
                VALUES (:actorUserId, :departmentId, :catalogueType, :queryText, :resultCount)
                RETURNING id, searched_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("actorUserId", log.actorUserId())
                .addValue("departmentId", log.departmentId(), Types.OTHER)
                .addValue("catalogueType", log.catalogueType().name())
                .addValue("queryText", log.queryText())
                .addValue("resultCount", log.resultCount());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ReferenceSearchLog(
                (UUID) rs.getObject("id"), log.actorUserId(), log.departmentId(), log.catalogueType(),
                log.queryText(), log.resultCount(), null, rs.getTimestamp("searched_at").toInstant()));
    }

    @Override
    public void recordSelection(UUID logId, String selectedId) {
        jdbcTemplate.update(
                "UPDATE reference_search_log SET selected_id = :selectedId WHERE id = :id",
                new MapSqlParameterSource().addValue("selectedId", selectedId).addValue("id", logId));
    }
}
