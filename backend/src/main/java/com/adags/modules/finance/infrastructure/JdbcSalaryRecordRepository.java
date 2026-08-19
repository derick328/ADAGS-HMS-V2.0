package com.adags.modules.finance.infrastructure;

import com.adags.modules.finance.domain.DeductionLine;
import com.adags.modules.finance.domain.DuplicateSalaryRecordException;
import com.adags.modules.finance.domain.PaymentMethod;
import com.adags.modules.finance.domain.SalaryPaymentStatus;
import com.adags.modules.finance.domain.SalaryRecord;
import com.adags.modules.finance.domain.SalaryRecordRepository;
import com.adags.shared.kernel.Money;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSalaryRecordRepository implements SalaryRecordRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, staff_id, month, year, basic_minor, overtime_hours, overtime_rate_minor, gross_minor,
                   deductions, net_minor, payment_method, payment_status, paid_at, notes, batch_id, currency_code, created_at
            FROM salary_record
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSalaryRecordRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public SalaryRecord insert(SalaryRecord record) {
        String sql = """
                INSERT INTO salary_record (staff_id, month, year, basic_minor, overtime_hours, overtime_rate_minor,
                                            gross_minor, deductions, net_minor, payment_method, batch_id, notes, currency_code)
                VALUES (:staffId, :month, :year, :basicMinor, :overtimeHours, :overtimeRateMinor, :grossMinor,
                        CAST(:deductions AS JSONB), :netMinor, :paymentMethod, :batchId, :notes, :currencyCode)
                RETURNING id, created_at
                """;
        String deductionsJson = writeDeductions(record.deductions());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("staffId", record.staffId())
                .addValue("month", record.month())
                .addValue("year", record.year())
                .addValue("basicMinor", record.basic().amountMinor())
                .addValue("overtimeHours", record.overtimeHours())
                .addValue("overtimeRateMinor", record.overtimeRate().amountMinor())
                .addValue("grossMinor", record.gross().amountMinor())
                .addValue("deductions", deductionsJson)
                .addValue("netMinor", record.net().amountMinor())
                .addValue("paymentMethod", record.paymentMethod() == null ? null : record.paymentMethod().name())
                .addValue("batchId", record.batchId())
                .addValue("notes", record.notes())
                .addValue("currencyCode", record.basic().currencyCode());

        try {
            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new SalaryRecord(
                    (UUID) rs.getObject("id"), record.staffId(), record.month(), record.year(), record.basic(),
                    record.overtimeHours(), record.overtimeRate(), record.gross(), record.deductions(), record.net(),
                    record.paymentMethod(), SalaryPaymentStatus.PENDING, null, record.notes(), record.batchId(),
                    rs.getTimestamp("created_at").toInstant()));
        } catch (DuplicateKeyException raced) {
            throw new DuplicateSalaryRecordException(record.staffId(), record.month(), record.year());
        }
    }

    @Override
    public Optional<SalaryRecord> findById(UUID id) {
        List<SalaryRecord> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<SalaryRecord> findByBatchId(UUID batchId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE batch_id = :batchId ORDER BY created_at",
                new MapSqlParameterSource("batchId", batchId), this::mapRow);
    }

    @Override
    public void markBatchPaid(UUID batchId, PaymentMethod paymentMethod) {
        jdbcTemplate.update("""
                        UPDATE salary_record
                        SET payment_status = 'PAID', paid_at = now(), payment_method = :paymentMethod
                        WHERE batch_id = :batchId
                        """,
                new MapSqlParameterSource()
                        .addValue("batchId", batchId)
                        .addValue("paymentMethod", paymentMethod.name()));
    }

    private String writeDeductions(List<DeductionLine> deductions) {
        try {
            return objectMapper.writeValueAsString(deductions);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize salary deductions", e);
        }
    }

    private List<DeductionLine> readDeductions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<DeductionLine>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize salary deductions", e);
        }
    }

    private SalaryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        String currencyCode = rs.getString("currency_code");
        String paymentMethod = rs.getString("payment_method");
        Timestamp paidAt = rs.getTimestamp("paid_at");
        Object batchId = rs.getObject("batch_id");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        return new SalaryRecord(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("staff_id"),
                rs.getInt("month"),
                rs.getInt("year"),
                new Money(rs.getLong("basic_minor"), currencyCode),
                rs.getBigDecimal("overtime_hours"),
                new Money(rs.getLong("overtime_rate_minor"), currencyCode),
                new Money(rs.getLong("gross_minor"), currencyCode),
                readDeductions(rs.getString("deductions")),
                new Money(rs.getLong("net_minor"), currencyCode),
                paymentMethod == null ? null : PaymentMethod.valueOf(paymentMethod),
                SalaryPaymentStatus.valueOf(rs.getString("payment_status")),
                paidAt == null ? null : paidAt.toInstant(),
                rs.getString("notes"),
                batchId == null ? null : (UUID) batchId,
                createdAt);
    }
}
