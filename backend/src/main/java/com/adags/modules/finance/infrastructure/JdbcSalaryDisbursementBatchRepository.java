package com.adags.modules.finance.infrastructure;

import com.adags.modules.finance.domain.BatchStatus;
import com.adags.modules.finance.domain.DuplicateBatchException;
import com.adags.modules.finance.domain.InvalidBatchTransitionException;
import com.adags.modules.finance.domain.SalaryDisbursementBatch;
import com.adags.modules.finance.domain.SalaryDisbursementBatchRepository;
import com.adags.shared.kernel.Money;
import org.springframework.dao.DuplicateKeyException;
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
public class JdbcSalaryDisbursementBatchRepository implements SalaryDisbursementBatchRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, batch_type, month, year, headcount, gross_total_minor, deductions_total_minor,
                   net_total_minor, currency_code, status, generated_by, generated_at, disbursed_by, disbursed_at,
                   reversed_by, reversed_at, reversal_reason
            FROM salary_disbursement_batch
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSalaryDisbursementBatchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SalaryDisbursementBatch insert(SalaryDisbursementBatch batch) {
        String sql = """
                INSERT INTO salary_disbursement_batch (batch_type, month, year, headcount, gross_total_minor,
                                                         deductions_total_minor, net_total_minor, currency_code, generated_by)
                VALUES (:batchType, :month, :year, :headcount, :grossTotalMinor, :deductionsTotalMinor,
                        :netTotalMinor, :currencyCode, :generatedBy)
                RETURNING id, generated_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchType", batch.batchType())
                .addValue("month", batch.month())
                .addValue("year", batch.year())
                .addValue("headcount", batch.headcount())
                .addValue("grossTotalMinor", batch.grossTotal().amountMinor())
                .addValue("deductionsTotalMinor", batch.deductionsTotal().amountMinor())
                .addValue("netTotalMinor", batch.netTotal().amountMinor())
                .addValue("currencyCode", batch.grossTotal().currencyCode())
                .addValue("generatedBy", batch.generatedBy());

        try {
            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new SalaryDisbursementBatch(
                    (UUID) rs.getObject("id"), batch.batchType(), batch.month(), batch.year(), batch.headcount(),
                    batch.grossTotal(), batch.deductionsTotal(), batch.netTotal(), BatchStatus.PENDING,
                    batch.generatedBy(), rs.getTimestamp("generated_at").toInstant(), null, null, null, null, null));
        } catch (DuplicateKeyException raced) {
            throw new DuplicateBatchException(batch.batchType(), batch.month(), batch.year());
        }
    }

    @Override
    public SalaryDisbursementBatch updateTotals(UUID batchId, int headcount, Money grossTotal, Money deductionsTotal,
                                                 Money netTotal) {
        jdbcTemplate.update("""
                        UPDATE salary_disbursement_batch
                        SET headcount = :headcount, gross_total_minor = :grossTotalMinor,
                            deductions_total_minor = :deductionsTotalMinor, net_total_minor = :netTotalMinor
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", batchId)
                        .addValue("headcount", headcount)
                        .addValue("grossTotalMinor", grossTotal.amountMinor())
                        .addValue("deductionsTotalMinor", deductionsTotal.amountMinor())
                        .addValue("netTotalMinor", netTotal.amountMinor()));
        return findById(batchId).orElseThrow();
    }

    @Override
    public Optional<SalaryDisbursementBatch> findById(UUID id) {
        List<SalaryDisbursementBatch> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<SalaryDisbursementBatch> findByPeriod(String batchType, int month, int year) {
        List<SalaryDisbursementBatch> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE batch_type = :batchType AND month = :month AND year = :year",
                new MapSqlParameterSource()
                        .addValue("batchType", batchType).addValue("month", month).addValue("year", year),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public SalaryDisbursementBatch disburse(UUID batchId, UUID disbursedBy) {
        String sql = """
                UPDATE salary_disbursement_batch
                SET status = 'DISBURSED', disbursed_by = :disbursedBy, disbursed_at = now()
                WHERE id = :id AND status = 'PENDING'
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", batchId).addValue("disbursedBy", disbursedBy));
        if (updated == 0) {
            SalaryDisbursementBatch current = findById(batchId).orElseThrow();
            throw new InvalidBatchTransitionException(batchId, current.status(), "disburse");
        }
        return findById(batchId).orElseThrow();
    }

    @Override
    public SalaryDisbursementBatch reverse(UUID batchId, UUID reversedBy, String reason) {
        String sql = """
                UPDATE salary_disbursement_batch
                SET status = 'REVERSED', reversed_by = :reversedBy, reversed_at = now(), reversal_reason = :reason
                WHERE id = :id AND status = 'DISBURSED'
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", batchId).addValue("reversedBy", reversedBy).addValue("reason", reason));
        if (updated == 0) {
            SalaryDisbursementBatch current = findById(batchId).orElseThrow();
            throw new InvalidBatchTransitionException(batchId, current.status(), "reverse");
        }
        return findById(batchId).orElseThrow();
    }

    private SalaryDisbursementBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
        String currencyCode = rs.getString("currency_code");
        Object disbursedBy = rs.getObject("disbursed_by");
        Timestamp disbursedAt = rs.getTimestamp("disbursed_at");
        Object reversedBy = rs.getObject("reversed_by");
        Timestamp reversedAt = rs.getTimestamp("reversed_at");
        return new SalaryDisbursementBatch(
                (UUID) rs.getObject("id"),
                rs.getString("batch_type"),
                rs.getInt("month"),
                rs.getInt("year"),
                rs.getInt("headcount"),
                new Money(rs.getLong("gross_total_minor"), currencyCode),
                new Money(rs.getLong("deductions_total_minor"), currencyCode),
                new Money(rs.getLong("net_total_minor"), currencyCode),
                BatchStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("generated_by"),
                rs.getTimestamp("generated_at").toInstant(),
                disbursedBy == null ? null : (UUID) disbursedBy,
                disbursedAt == null ? null : disbursedAt.toInstant(),
                reversedBy == null ? null : (UUID) reversedBy,
                reversedAt == null ? null : reversedAt.toInstant(),
                rs.getString("reversal_reason"));
    }
}
