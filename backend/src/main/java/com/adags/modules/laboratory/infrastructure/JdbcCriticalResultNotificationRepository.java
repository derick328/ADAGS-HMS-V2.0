package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.CriticalResultNotification;
import com.adags.modules.laboratory.domain.CriticalResultNotificationRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCriticalResultNotificationRepository implements CriticalResultNotificationRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, result_id, parameter_id, value, notified_user_id, notified_at,
                   acknowledged_at, acknowledged_by, escalated_at, escalated_to_user_id, created_at
            FROM critical_result_notification
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCriticalResultNotificationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CriticalResultNotification insert(CriticalResultNotification notification) {
        String sql = """
                INSERT INTO critical_result_notification (result_id, parameter_id, value, notified_user_id)
                VALUES (:resultId, :parameterId, :value, :notifiedUserId)
                RETURNING id, notified_at, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("resultId", notification.resultId())
                .addValue("parameterId", notification.parameterId())
                .addValue("value", notification.value())
                .addValue("notifiedUserId", notification.notifiedUserId());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new CriticalResultNotification(
                (UUID) rs.getObject("id"), notification.resultId(), notification.parameterId(), notification.value(),
                notification.notifiedUserId(), rs.getTimestamp("notified_at").toInstant(), null, null, null, null,
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<CriticalResultNotification> findById(UUID id) {
        List<CriticalResultNotification> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<CriticalResultNotification> findByResultId(UUID resultId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE result_id = :resultId",
                new MapSqlParameterSource("resultId", resultId), this::mapRow);
    }

    @Override
    public boolean acknowledge(UUID id, UUID acknowledgedBy) {
        int updated = jdbcTemplate.update("""
                        UPDATE critical_result_notification
                        SET acknowledged_at = now(), acknowledged_by = :acknowledgedBy
                        WHERE id = :id AND acknowledged_at IS NULL
                        """,
                new MapSqlParameterSource().addValue("id", id).addValue("acknowledgedBy", acknowledgedBy));
        return updated == 1;
    }

    @Override
    public List<CriticalResultNotification> findOverdueUnescalated(Instant cutoff) {
        String sql = SELECT_COLUMNS + """
                 WHERE acknowledged_at IS NULL AND escalated_at IS NULL AND notified_at < :cutoff
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)), this::mapRow);
    }

    @Override
    public boolean escalate(UUID id, UUID escalatedToUserId) {
        int updated = jdbcTemplate.update("""
                        UPDATE critical_result_notification
                        SET escalated_at = now(), escalated_to_user_id = :escalatedToUserId
                        WHERE id = :id AND acknowledged_at IS NULL AND escalated_at IS NULL
                        """,
                new MapSqlParameterSource().addValue("id", id).addValue("escalatedToUserId", escalatedToUserId));
        return updated == 1;
    }

    private CriticalResultNotification mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp acknowledgedAt = rs.getTimestamp("acknowledged_at");
        Timestamp escalatedAt = rs.getTimestamp("escalated_at");
        Object acknowledgedBy = rs.getObject("acknowledged_by");
        Object escalatedToUserId = rs.getObject("escalated_to_user_id");
        return new CriticalResultNotification(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("result_id"),
                (UUID) rs.getObject("parameter_id"),
                rs.getDouble("value"),
                (UUID) rs.getObject("notified_user_id"),
                rs.getTimestamp("notified_at").toInstant(),
                acknowledgedAt == null ? null : acknowledgedAt.toInstant(),
                acknowledgedBy == null ? null : (UUID) acknowledgedBy,
                escalatedAt == null ? null : escalatedAt.toInstant(),
                escalatedToUserId == null ? null : (UUID) escalatedToUserId,
                rs.getTimestamp("created_at").toInstant());
    }
}
