package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.CreditNote;
import com.adags.modules.billing.domain.CreditNoteRepository;
import com.adags.modules.billing.domain.CreditNoteStatus;
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
public class JdbcCreditNoteRepository implements CreditNoteRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, invoice_id, amount_minor, currency_code, reason, reference, issued_by, issued_at,
                   requires_second_signature, status, approved_by, approved_at, decision_note
            FROM credit_note
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCreditNoteRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CreditNote insert(CreditNote creditNote) {
        String sql = """
                INSERT INTO credit_note (invoice_id, amount_minor, currency_code, reason, reference, issued_by,
                                          requires_second_signature)
                VALUES (:invoiceId, :amountMinor, :currencyCode, :reason, :reference, :issuedBy, :requiresSecondSignature)
                RETURNING id, issued_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("invoiceId", creditNote.invoiceId())
                .addValue("amountMinor", creditNote.amount().amountMinor())
                .addValue("currencyCode", creditNote.amount().currencyCode())
                .addValue("reason", creditNote.reason())
                .addValue("reference", creditNote.reference())
                .addValue("issuedBy", creditNote.issuedBy())
                .addValue("requiresSecondSignature", creditNote.requiresSecondSignature());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new CreditNote(
                (UUID) rs.getObject("id"), creditNote.invoiceId(), creditNote.amount(), creditNote.reason(),
                creditNote.reference(), creditNote.issuedBy(), rs.getTimestamp("issued_at").toInstant(),
                creditNote.requiresSecondSignature(), CreditNoteStatus.PENDING, null, null, null));
    }

    @Override
    public Optional<CreditNote> findById(UUID id) {
        List<CreditNote> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<CreditNote> findPending() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE status = 'PENDING' ORDER BY issued_at ASC",
                new MapSqlParameterSource(), this::mapRow);
    }

    @Override
    public CreditNote decide(UUID id, CreditNoteStatus status, UUID approvedBy, String decisionNote) {
        jdbcTemplate.update("""
                        UPDATE credit_note
                        SET status = :status, approved_by = :approvedBy, approved_at = now(), decision_note = :decisionNote
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("approvedBy", approvedBy)
                        .addValue("decisionNote", decisionNote));
        return findById(id).orElseThrow();
    }

    private CreditNote mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object approvedBy = rs.getObject("approved_by");
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        return new CreditNote(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("invoice_id"),
                new Money(rs.getLong("amount_minor"), rs.getString("currency_code")),
                rs.getString("reason"),
                rs.getString("reference"),
                (UUID) rs.getObject("issued_by"),
                rs.getTimestamp("issued_at").toInstant(),
                rs.getBoolean("requires_second_signature"),
                CreditNoteStatus.valueOf(rs.getString("status")),
                approvedBy == null ? null : (UUID) approvedBy,
                approvedAt == null ? null : approvedAt.toInstant(),
                rs.getString("decision_note"));
    }
}
