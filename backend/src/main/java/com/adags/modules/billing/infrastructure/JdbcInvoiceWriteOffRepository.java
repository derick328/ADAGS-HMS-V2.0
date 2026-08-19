package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.InvoiceWriteOff;
import com.adags.modules.billing.domain.InvoiceWriteOffRepository;
import com.adags.modules.billing.domain.WriteOffStatus;
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
public class JdbcInvoiceWriteOffRepository implements InvoiceWriteOffRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, invoice_id, amount_minor, currency_code, reason, requested_by, requested_at,
                   requires_second_signature, status, decided_by, decided_at, decision_note
            FROM invoice_write_off
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInvoiceWriteOffRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public InvoiceWriteOff insert(InvoiceWriteOff writeOff) {
        String sql = """
                INSERT INTO invoice_write_off (invoice_id, amount_minor, currency_code, reason, requested_by,
                                                requires_second_signature)
                VALUES (:invoiceId, :amountMinor, :currencyCode, :reason, :requestedBy, :requiresSecondSignature)
                RETURNING id, requested_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("invoiceId", writeOff.invoiceId())
                .addValue("amountMinor", writeOff.amount().amountMinor())
                .addValue("currencyCode", writeOff.amount().currencyCode())
                .addValue("reason", writeOff.reason())
                .addValue("requestedBy", writeOff.requestedBy())
                .addValue("requiresSecondSignature", writeOff.requiresSecondSignature());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new InvoiceWriteOff(
                (UUID) rs.getObject("id"), writeOff.invoiceId(), writeOff.amount(), writeOff.reason(),
                writeOff.requestedBy(), rs.getTimestamp("requested_at").toInstant(),
                writeOff.requiresSecondSignature(), WriteOffStatus.PENDING, null, null, null));
    }

    @Override
    public Optional<InvoiceWriteOff> findById(UUID id) {
        List<InvoiceWriteOff> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<InvoiceWriteOff> findPending() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE status = 'PENDING' ORDER BY requested_at ASC",
                new MapSqlParameterSource(), this::mapRow);
    }

    @Override
    public InvoiceWriteOff decide(UUID id, WriteOffStatus status, UUID decidedBy, String decisionNote) {
        jdbcTemplate.update("""
                        UPDATE invoice_write_off
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

    private InvoiceWriteOff mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object decidedBy = rs.getObject("decided_by");
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new InvoiceWriteOff(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("invoice_id"),
                new Money(rs.getLong("amount_minor"), rs.getString("currency_code")),
                rs.getString("reason"),
                (UUID) rs.getObject("requested_by"),
                rs.getTimestamp("requested_at").toInstant(),
                rs.getBoolean("requires_second_signature"),
                WriteOffStatus.valueOf(rs.getString("status")),
                decidedBy == null ? null : (UUID) decidedBy,
                decidedAt == null ? null : decidedAt.toInstant(),
                rs.getString("decision_note"));
    }
}
