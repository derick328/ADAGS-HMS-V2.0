package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.PaymentBypassSource;
import com.adags.modules.billing.domain.PaymentBypassStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcPaymentBypassRepository implements PaymentBypassRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, source, reason, status, granted_by, granted_at, revoked_by, revoked_at
            FROM payment_bypass
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentBypassRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PaymentBypass insert(PaymentBypass paymentBypass) {
        String sql = """
                INSERT INTO payment_bypass (encounter_id, source, reason, status, granted_by)
                VALUES (:encounterId, :source, :reason, :status, :grantedBy)
                RETURNING id, granted_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("encounterId", paymentBypass.encounterId())
                .addValue("source", paymentBypass.source().name())
                .addValue("reason", paymentBypass.reason())
                .addValue("status", paymentBypass.status().name())
                .addValue("grantedBy", paymentBypass.grantedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new PaymentBypass(
                (UUID) rs.getObject("id"),
                paymentBypass.encounterId(),
                paymentBypass.source(),
                paymentBypass.reason(),
                paymentBypass.status(),
                paymentBypass.grantedBy(),
                rs.getTimestamp("granted_at").toInstant(),
                null,
                null));
    }

    @Override
    public List<PaymentBypass> findActiveByEncounterId(UUID encounterId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE encounter_id = :encounterId AND status = 'ACTIVE'",
                new MapSqlParameterSource("encounterId", encounterId),
                this::mapRow);
    }

    @Override
    public List<PaymentBypass> findAllActive() {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = 'ACTIVE' ORDER BY granted_at",
                this::mapRow);
    }

    private PaymentBypass mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new PaymentBypass(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                PaymentBypassSource.valueOf(rs.getString("source")),
                rs.getString("reason"),
                PaymentBypassStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("granted_by"),
                rs.getTimestamp("granted_at").toInstant(),
                (UUID) rs.getObject("revoked_by"),
                revokedAt == null ? null : revokedAt.toInstant());
    }
}
