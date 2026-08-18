package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceNotEditableException;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInvoiceLineRepository implements InvoiceLineRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInvoiceLineRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public InvoiceLine insert(InvoiceLine line) {
        // The WHERE EXISTS guard is the "lines locked once ISSUED" rule
        // enforced in the same statement as the insert — a stale read of
        // a since-issued invoice inserts nothing rather than silently
        // appending to a locked, already-numbered financial document.
        String sql = """
                INSERT INTO invoice_line (invoice_id, service_item_id, quantity, gross_minor,
                                           insurance_portion_minor, patient_portion_minor, currency_code,
                                           coverage_rule_id, authorization_id, source_event_type, source_event_id)
                SELECT :invoiceId, :serviceItemId, :quantity, :grossMinor, :insurancePortionMinor,
                       :patientPortionMinor, :currencyCode, :coverageRuleId, :authorizationId, :sourceEventType,
                       :sourceEventId
                WHERE EXISTS (SELECT 1 FROM invoice WHERE id = :invoiceId AND state = 'DRAFT')
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("invoiceId", line.invoiceId())
                .addValue("serviceItemId", line.serviceItemId())
                .addValue("quantity", line.quantity())
                .addValue("grossMinor", line.grossAmount().amountMinor())
                .addValue("insurancePortionMinor", line.insurancePortion().amountMinor())
                .addValue("patientPortionMinor", line.patientPortion().amountMinor())
                .addValue("currencyCode", line.grossAmount().currencyCode())
                .addValue("coverageRuleId", line.coverageRuleId(), Types.OTHER)
                .addValue("authorizationId", line.authorizationId(), Types.OTHER)
                .addValue("sourceEventType", line.sourceEventType())
                .addValue("sourceEventId", line.sourceEventId());

        List<InvoiceLine> inserted = jdbcTemplate.query(sql, params, (rs, rowNum) -> new InvoiceLine(
                (UUID) rs.getObject("id"), line.invoiceId(), line.serviceItemId(), line.quantity(),
                line.grossAmount(), line.insurancePortion(), line.patientPortion(), line.coverageRuleId(),
                line.authorizationId(), line.sourceEventType(), line.sourceEventId(),
                rs.getTimestamp("created_at").toInstant()));

        if (inserted.isEmpty()) {
            throw new InvoiceNotEditableException(line.invoiceId());
        }
        return inserted.get(0);
    }

    @Override
    public Optional<InvoiceLine> findById(UUID id) {
        String sql = """
                SELECT id, invoice_id, service_item_id, quantity, gross_minor, insurance_portion_minor,
                       patient_portion_minor, currency_code, coverage_rule_id, authorization_id,
                       source_event_type, source_event_id, created_at
                FROM invoice_line
                WHERE id = :id
                """;
        List<InvoiceLine> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public boolean setAuthorization(UUID invoiceLineId, UUID authorizationId) {
        int updated = jdbcTemplate.update(
                "UPDATE invoice_line SET authorization_id = :authorizationId WHERE id = :id AND authorization_id IS NULL",
                new MapSqlParameterSource()
                        .addValue("authorizationId", authorizationId)
                        .addValue("id", invoiceLineId));
        return updated == 1;
    }

    @Override
    public Optional<InvoiceLine> findConsultationLineByEncounterId(UUID encounterId) {
        String sql = """
                SELECT il.id, il.invoice_id, il.service_item_id, il.quantity, il.gross_minor,
                       il.insurance_portion_minor, il.patient_portion_minor, il.currency_code,
                       il.coverage_rule_id, il.authorization_id, il.source_event_type, il.source_event_id,
                       il.created_at
                FROM invoice_line il
                JOIN invoice i ON i.id = il.invoice_id
                WHERE i.encounter_id = :encounterId AND il.source_event_type = 'TriageCompleted'
                ORDER BY il.created_at DESC
                LIMIT 1
                """;
        List<InvoiceLine> results = jdbcTemplate.query(
                sql, new MapSqlParameterSource("encounterId", encounterId), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<InvoiceLine> findByInvoiceId(UUID invoiceId) {
        String sql = """
                SELECT id, invoice_id, service_item_id, quantity, gross_minor, insurance_portion_minor,
                       patient_portion_minor, currency_code, coverage_rule_id, authorization_id,
                       source_event_type, source_event_id, created_at
                FROM invoice_line
                WHERE invoice_id = :invoiceId
                ORDER BY created_at
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("invoiceId", invoiceId), this::mapRow);
    }

    private InvoiceLine mapRow(ResultSet rs, int rowNum) throws SQLException {
        String currencyCode = rs.getString("currency_code");
        Object coverageRuleId = rs.getObject("coverage_rule_id");
        Object authorizationId = rs.getObject("authorization_id");
        return new InvoiceLine(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("invoice_id"),
                (UUID) rs.getObject("service_item_id"),
                rs.getInt("quantity"),
                new Money(rs.getLong("gross_minor"), currencyCode),
                new Money(rs.getLong("insurance_portion_minor"), currencyCode),
                new Money(rs.getLong("patient_portion_minor"), currencyCode),
                coverageRuleId == null ? null : (UUID) coverageRuleId,
                authorizationId == null ? null : (UUID) authorizationId,
                rs.getString("source_event_type"),
                (UUID) rs.getObject("source_event_id"),
                rs.getTimestamp("created_at").toInstant());
    }
}
