package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
import com.adags.modules.inpatient.domain.WardAlertSourceType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcWardAlertNotificationRepository implements WardAlertNotificationRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, admission_id, source_type, source_id, detail, notified_user_id, notified_at,
                   acknowledged_at, acknowledged_by, escalated_at, escalated_to_user_id, created_at
            FROM ward_alert_notification
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWardAlertNotificationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WardAlertNotification insert(WardAlertNotification notification) {
        String sql = """
                INSERT INTO ward_alert_notification (admission_id, source_type, source_id, detail, notified_user_id)
                VALUES (:admissionId, :sourceType, :sourceId, :detail, :notifiedUserId)
                RETURNING id, notified_at, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("admissionId", notification.admissionId())
                .addValue("sourceType", notification.sourceType().name())
                .addValue("sourceId", notification.sourceId())
                .addValue("detail", notification.detail())
                .addValue("notifiedUserId", notification.notifiedUserId());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new WardAlertNotification(
                (UUID) rs.getObject("id"), notification.admissionId(), notification.sourceType(),
                notification.sourceId(), notification.detail(), notification.notifiedUserId(),
                rs.getTimestamp("notified_at").toInstant(), null, null, null, null,
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<WardAlertNotification> findByAdmissionId(UUID admissionId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE admission_id = :admissionId ORDER BY notified_at DESC",
                new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
    }

    @Override
    public List<WardAlertNotification> findUnacknowledgedByAdmissionId(UUID admissionId) {
        String sql = SELECT_COLUMNS + " WHERE admission_id = :admissionId AND acknowledged_at IS NULL "
                + "ORDER BY notified_at DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
    }

    @Override
    public boolean acknowledge(UUID notificationId, UUID acknowledgedBy) {
        String sql = "UPDATE ward_alert_notification SET acknowledged_at = now(), acknowledged_by = :acknowledgedBy "
                + "WHERE id = :id AND acknowledged_at IS NULL";
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("acknowledgedBy", acknowledgedBy)
                .addValue("id", notificationId));
        return updated > 0;
    }

    @Override
    public List<WardAlertNotification> findOverdueUnescalated(Instant cutoff) {
        String sql = SELECT_COLUMNS + " WHERE acknowledged_at IS NULL AND escalated_at IS NULL "
                + "AND notified_at < :cutoff";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)), this::mapRow);
    }

    @Override
    public boolean escalate(UUID notificationId, UUID escalatedToUserId) {
        String sql = "UPDATE ward_alert_notification SET escalated_at = now(), escalated_to_user_id = :escalatedToUserId "
                + "WHERE id = :id AND escalated_at IS NULL";
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("escalatedToUserId", escalatedToUserId)
                .addValue("id", notificationId));
        return updated > 0;
    }

    private WardAlertNotification mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp acknowledgedAt = rs.getTimestamp("acknowledged_at");
        Timestamp escalatedAt = rs.getTimestamp("escalated_at");
        return new WardAlertNotification(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("admission_id"),
                WardAlertSourceType.valueOf(rs.getString("source_type")),
                (UUID) rs.getObject("source_id"),
                rs.getString("detail"),
                (UUID) rs.getObject("notified_user_id"),
                rs.getTimestamp("notified_at").toInstant(),
                acknowledgedAt == null ? null : acknowledgedAt.toInstant(),
                (UUID) rs.getObject("acknowledged_by"),
                escalatedAt == null ? null : escalatedAt.toInstant(),
                (UUID) rs.getObject("escalated_to_user_id"),
                rs.getTimestamp("created_at").toInstant());
    }
}
