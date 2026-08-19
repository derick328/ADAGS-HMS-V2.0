package com.adags.modules.finance.infrastructure;

import com.adags.modules.finance.domain.CalculationMethod;
import com.adags.modules.finance.domain.DeductionRule;
import com.adags.modules.finance.domain.DeductionRuleRepository;
import com.adags.modules.finance.domain.EmploymentType;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDeductionRuleRepository implements DeductionRuleRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, employment_type, deduction_code, calculation_method, rate_or_amount, threshold_minor,
                   is_employer_paid, effective_from, effective_to, created_by, created_at
            FROM deduction_rule
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDeductionRuleRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DeductionRule insert(DeductionRule rule) {
        String sql = """
                INSERT INTO deduction_rule (employment_type, deduction_code, calculation_method, rate_or_amount,
                                             threshold_minor, is_employer_paid, effective_from, created_by)
                VALUES (:employmentType, :deductionCode, :calculationMethod, :rateOrAmount, :thresholdMinor,
                        :isEmployerPaid, :effectiveFrom, :createdBy)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("employmentType", rule.employmentType().name())
                .addValue("deductionCode", rule.deductionCode())
                .addValue("calculationMethod", rule.calculationMethod().name())
                .addValue("rateOrAmount", rule.rateOrAmount())
                .addValue("thresholdMinor", rule.threshold() == null ? null : rule.threshold().amountMinor())
                .addValue("isEmployerPaid", rule.isEmployerPaid())
                .addValue("effectiveFrom", rule.effectiveFrom())
                .addValue("createdBy", rule.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new DeductionRule(
                (UUID) rs.getObject("id"), rule.employmentType(), rule.deductionCode(), rule.calculationMethod(),
                rule.rateOrAmount(), rule.threshold(), rule.isEmployerPaid(), rule.effectiveFrom(), null,
                rule.createdBy(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public void close(UUID ruleId, LocalDate effectiveTo) {
        jdbcTemplate.update("UPDATE deduction_rule SET effective_to = :effectiveTo WHERE id = :id",
                new MapSqlParameterSource().addValue("id", ruleId).addValue("effectiveTo", effectiveTo));
    }

    @Override
    public Optional<DeductionRule> findById(UUID id) {
        List<DeductionRule> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<DeductionRule> findCurrent(EmploymentType employmentType, String deductionCode) {
        List<DeductionRule> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE employment_type = :employmentType AND deduction_code = :deductionCode AND effective_to IS NULL",
                new MapSqlParameterSource()
                        .addValue("employmentType", employmentType.name())
                        .addValue("deductionCode", deductionCode),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<DeductionRule> findEffective(EmploymentType employmentType, LocalDate asOfDate) {
        String sql = SELECT_COLUMNS + """
                WHERE employment_type = :employmentType
                  AND effective_from <= :asOfDate
                  AND (effective_to IS NULL OR effective_to >= :asOfDate)
                ORDER BY deduction_code
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("employmentType", employmentType.name())
                .addValue("asOfDate", asOfDate), this::mapRow);
    }

    private DeductionRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object thresholdMinor = rs.getObject("threshold_minor");
        Date effectiveTo = rs.getDate("effective_to");
        return new DeductionRule(
                (UUID) rs.getObject("id"),
                EmploymentType.valueOf(rs.getString("employment_type")),
                rs.getString("deduction_code"),
                CalculationMethod.valueOf(rs.getString("calculation_method")),
                rs.getBigDecimal("rate_or_amount"),
                thresholdMinor == null ? null : new Money(((Number) thresholdMinor).longValue(), "TZS"),
                rs.getBoolean("is_employer_paid"),
                rs.getDate("effective_from").toLocalDate(),
                effectiveTo == null ? null : effectiveTo.toLocalDate(),
                (UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
