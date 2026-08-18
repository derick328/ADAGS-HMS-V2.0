package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceCannotBeVoidedException;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.StaleInvoiceException;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInvoiceRepository implements InvoiceRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, patient_id, facility_id, state, payer_category, insurance_policy_id,
                   invoice_number, gross_total_minor, insurance_portion_total_minor, patient_portion_total_minor,
                   outstanding_total_minor, void_reason, voided_by, voided_at, currency_code, version,
                   created_at, created_by
            FROM invoice
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInvoiceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Invoice insertDraft(Invoice invoice) {
        String sql = """
                INSERT INTO invoice (encounter_id, patient_id, facility_id, payer_category, insurance_policy_id,
                                      currency_code, created_by)
                VALUES (:encounterId, :patientId, :facilityId, :payerCategory, :insurancePolicyId, :currencyCode,
                        :createdBy)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("encounterId", invoice.encounterId(), Types.OTHER)
                .addValue("patientId", invoice.patientId())
                .addValue("facilityId", invoice.facilityId())
                .addValue("payerCategory", invoice.payerCategory().name())
                .addValue("insurancePolicyId", invoice.insurancePolicyId(), Types.OTHER)
                .addValue("currencyCode", invoice.grossTotal().currencyCode())
                .addValue("createdBy", invoice.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Invoice(
                (UUID) rs.getObject("id"), invoice.encounterId(), invoice.patientId(), invoice.facilityId(),
                InvoiceState.DRAFT, invoice.payerCategory(), invoice.insurancePolicyId(), null,
                invoice.grossTotal(), invoice.insurancePortionTotal(), invoice.patientPortionTotal(),
                invoice.outstandingTotal(), null, null, null, 0,
                rs.getTimestamp("created_at").toInstant(), invoice.createdBy()));
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        List<Invoice> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Invoice issue(UUID invoiceId, String invoiceNumber, int expectedVersion) {
        // The subquery is the totals recompute: derived fresh from
        // invoice_line at the moment of issue rather than trusted from
        // accumulated writes, same "trust the ledger for truth" reasoning
        // as §9.3. state = 'DRAFT' AND version = :expectedVersion in one
        // WHERE covers both "already issued" and "changed underneath you"
        // — either way, zero rows updated means reload and resubmit.
        // outstanding_total_minor starts equal to gross — nothing paid yet.
        String sql = """
                UPDATE invoice
                SET state = 'ISSUED',
                    invoice_number = :invoiceNumber,
                    gross_total_minor = sub.gross,
                    insurance_portion_total_minor = sub.insurance,
                    patient_portion_total_minor = sub.patient,
                    outstanding_total_minor = sub.gross,
                    version = version + 1
                FROM (
                    SELECT COALESCE(SUM(gross_minor), 0) AS gross,
                           COALESCE(SUM(insurance_portion_minor), 0) AS insurance,
                           COALESCE(SUM(patient_portion_minor), 0) AS patient
                    FROM invoice_line WHERE invoice_id = :invoiceId
                ) sub
                WHERE invoice.id = :invoiceId AND state = 'DRAFT' AND version = :expectedVersion
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("invoiceId", invoiceId)
                .addValue("invoiceNumber", invoiceNumber)
                .addValue("expectedVersion", expectedVersion));
        if (updated == 0) {
            throw new StaleInvoiceException(invoiceId, expectedVersion);
        }
        return findById(invoiceId).orElseThrow();
    }

    @Override
    public Invoice recordSettlement(UUID invoiceId, Money settledAmount) {
        // The CASE only fires the transition from ISSUED, and only once —
        // once state is PATIENT_PORTION_SETTLED, this condition no longer
        // matches, so a later call (e.g. a second line on the same
        // invoice reaching zero) just leaves state as is.
        String sql = """
                UPDATE invoice
                SET outstanding_total_minor = outstanding_total_minor - :settledAmountMinor,
                    state = CASE
                                WHEN state = 'ISSUED' AND outstanding_total_minor - :settledAmountMinor <= 0
                                THEN 'PATIENT_PORTION_SETTLED'
                                ELSE state
                            END
                WHERE id = :invoiceId
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("invoiceId", invoiceId)
                .addValue("settledAmountMinor", settledAmount.amountMinor()));
        return findById(invoiceId).orElseThrow();
    }

    @Override
    public Invoice voidUnpaid(UUID invoiceId, String reason, UUID voidedBy) {
        String sql = """
                UPDATE invoice
                SET state = 'VOID', void_reason = :reason, voided_by = :voidedBy, voided_at = now()
                WHERE id = :invoiceId AND state = 'ISSUED' AND outstanding_total_minor = gross_total_minor
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("invoiceId", invoiceId)
                .addValue("reason", reason)
                .addValue("voidedBy", voidedBy));
        if (updated == 0) {
            throw new InvoiceCannotBeVoidedException(invoiceId);
        }
        return findById(invoiceId).orElseThrow();
    }

    private Invoice mapRow(ResultSet rs, int rowNum) throws SQLException {
        String currencyCode = rs.getString("currency_code");
        Object insurancePolicyId = rs.getObject("insurance_policy_id");
        Object voidedBy = rs.getObject("voided_by");
        Timestamp voidedAt = rs.getTimestamp("voided_at");
        return new Invoice(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("facility_id"),
                InvoiceState.valueOf(rs.getString("state")),
                PayerCategory.valueOf(rs.getString("payer_category")),
                insurancePolicyId == null ? null : (UUID) insurancePolicyId,
                rs.getString("invoice_number"),
                new Money(rs.getLong("gross_total_minor"), currencyCode),
                new Money(rs.getLong("insurance_portion_total_minor"), currencyCode),
                new Money(rs.getLong("patient_portion_total_minor"), currencyCode),
                new Money(rs.getLong("outstanding_total_minor"), currencyCode),
                rs.getString("void_reason"),
                voidedBy == null ? null : (UUID) voidedBy,
                voidedAt == null ? null : voidedAt.toInstant(),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant(),
                (UUID) rs.getObject("created_by"));
    }
}
