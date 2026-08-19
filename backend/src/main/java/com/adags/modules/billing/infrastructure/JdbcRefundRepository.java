package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.Refund;
import com.adags.modules.billing.domain.RefundRepository;
import com.adags.modules.billing.domain.RefundStatus;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRefundRepository implements RefundRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, payment_allocation_id, amount_minor, currency_code, reason, requested_by, requested_at,
                   requires_second_signature, status, decided_by, decided_at, decision_note
            FROM refund
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRefundRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Refund insert(Refund refund) {
        String sql = """
                INSERT INTO refund (payment_allocation_id, amount_minor, currency_code, reason, requested_by,
                                     requires_second_signature)
                VALUES (:paymentAllocationId, :amountMinor, :currencyCode, :reason, :requestedBy, :requiresSecondSignature)
                RETURNING id, requested_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("paymentAllocationId", refund.paymentAllocationId())
                .addValue("amountMinor", refund.amount().amountMinor())
                .addValue("currencyCode", refund.amount().currencyCode())
                .addValue("reason", refund.reason())
                .addValue("requestedBy", refund.requestedBy())
                .addValue("requiresSecondSignature", refund.requiresSecondSignature());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Refund(
                (UUID) rs.getObject("id"), refund.paymentAllocationId(), refund.amount(), refund.reason(),
                refund.requestedBy(), rs.getTimestamp("requested_at").toInstant(),
                refund.requiresSecondSignature(), RefundStatus.PENDING, null, null, null));
    }

    @Override
    public Optional<Refund> findById(UUID id) {
        List<Refund> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Refund> findPending() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE status = 'PENDING' ORDER BY requested_at ASC",
                new MapSqlParameterSource(), this::mapRow);
    }

    @Override
    public Refund decide(UUID id, RefundStatus status, UUID decidedBy, String decisionNote) {
        jdbcTemplate.update("""
                        UPDATE refund
                        SET status = :status, decided_by = :decidedBy, decided_at = now(), decision_note = :decisionNote
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("decidedBy", decidedBy)
                        .addValue("decisionNote", decisionNote));
        return findById(id).orElseThrow();
    }

    private Refund mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object decidedBy = rs.getObject("decided_by");
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new Refund(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("payment_allocation_id"),
                new Money(rs.getLong("amount_minor"), rs.getString("currency_code")),
                rs.getString("reason"),
                (UUID) rs.getObject("requested_by"),
                rs.getTimestamp("requested_at").toInstant(),
                rs.getBoolean("requires_second_signature"),
                RefundStatus.valueOf(rs.getString("status")),
                decidedBy == null ? null : (UUID) decidedBy,
                decidedAt == null ? null : decidedAt.toInstant(),
                rs.getString("decision_note"));
    }
}
