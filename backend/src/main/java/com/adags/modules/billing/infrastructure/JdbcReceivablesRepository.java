package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.AgingBucket;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.ReceivableInvoice;
import com.adags.modules.billing.domain.ReceivableSummary;
import com.adags.modules.billing.domain.ReceivablesRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Deliberately separate from JdbcInvoiceRepository — a read model over
// RECEIVABLE_POST_DISCHARGE invoices, not the write-side entity repository.
// The aging bucket is computed in Java from aging_days (AgingBucket.
// fromAgingDays), not duplicated as a second CASE expression here, so the
// three-way boundary (30/60/90) has exactly one place it can drift.
@Repository
public class JdbcReceivablesRepository implements ReceivablesRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcReceivablesRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ReceivableSummary> summarizeAging() {
        Map<PayerCategory, Map<AgingBucket, List<ReceivableInvoice>>> grouped = findReceivables(null, null).stream()
                .collect(Collectors.groupingBy(ReceivableInvoice::payerCategory,
                        Collectors.groupingBy(ReceivableInvoice::bucket)));

        return grouped.entrySet().stream()
                .flatMap(byPayer -> byPayer.getValue().entrySet().stream().map(byBucket -> {
                    List<ReceivableInvoice> rows = byBucket.getValue();
                    String currencyCode = rows.get(0).outstandingTotal().currencyCode();
                    long totalMinor = rows.stream().mapToLong(r -> r.outstandingTotal().amountMinor()).sum();
                    return new ReceivableSummary(byPayer.getKey(), byBucket.getKey(), rows.size(),
                            new Money(totalMinor, currencyCode));
                }))
                .sorted((a, b) -> {
                    int byPayer = a.payerCategory().compareTo(b.payerCategory());
                    return byPayer != 0 ? byPayer : a.bucket().compareTo(b.bucket());
                })
                .toList();
    }

    @Override
    public List<ReceivableInvoice> findReceivables(PayerCategory payerCategory, AgingBucket bucket) {
        // Both filters applied in Java, not SQL: a "= :param OR :param IS
        // NULL" WHERE clause hits Postgres's parameter type inference when
        // the bound value is null (JDBC sends it with no type OID) —
        // simpler to filter the small result set here than fight that with
        // an explicit cast. Same reasoning bucket filtering already used.
        String sql = """
                SELECT id, invoice_number, patient_id, encounter_id, payer_category, outstanding_total_minor,
                       currency_code, receivable_at, (CURRENT_DATE - receivable_at::date) AS aging_days
                FROM invoice
                WHERE state = 'RECEIVABLE_POST_DISCHARGE'
                ORDER BY receivable_at ASC
                """;
        return jdbcTemplate.query(sql, this::mapRow).stream()
                .filter(r -> payerCategory == null || r.payerCategory() == payerCategory)
                .filter(r -> bucket == null || r.bucket() == bucket)
                .toList();
    }

    private ReceivableInvoice mapRow(ResultSet rs, int rowNum) throws SQLException {
        long agingDays = rs.getLong("aging_days");
        return new ReceivableInvoice(
                (UUID) rs.getObject("id"),
                rs.getString("invoice_number"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("encounter_id"),
                PayerCategory.valueOf(rs.getString("payer_category")),
                new Money(rs.getLong("outstanding_total_minor"), rs.getString("currency_code")),
                rs.getTimestamp("receivable_at").toInstant(),
                agingDays,
                AgingBucket.fromAgingDays(agingDays));
    }
}
