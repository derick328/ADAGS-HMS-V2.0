package com.adags.modules.insurance.infrastructure;

import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.SchemeCoverageRuleRepository;
import com.adags.modules.insurance.domain.ServiceCategory;
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
public class JdbcSchemeCoverageRuleRepository implements SchemeCoverageRuleRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, scheme_id, service_category, service_item_id, coverage_type, coverage_value,
                   requires_preauth, effective_from, effective_to, created_by, created_at
            FROM scheme_coverage_rule
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSchemeCoverageRuleRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SchemeCoverageRule insert(SchemeCoverageRule rule) {
        String sql = """
                INSERT INTO scheme_coverage_rule (scheme_id, service_category, service_item_id, coverage_type,
                                                    coverage_value, requires_preauth, effective_from, created_by)
                VALUES (:schemeId, :serviceCategory, :serviceItemId, :coverageType, :coverageValue, :requiresPreauth,
                        :effectiveFrom, :createdBy)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("schemeId", rule.schemeId())
                .addValue("serviceCategory", rule.serviceCategory() == null ? null : rule.serviceCategory().name())
                .addValue("serviceItemId", rule.serviceItemId())
                .addValue("coverageType", rule.coverageType().name())
                .addValue("coverageValue", rule.coverageValue())
                .addValue("requiresPreauth", rule.requiresPreauth())
                .addValue("effectiveFrom", rule.effectiveFrom())
                .addValue("createdBy", rule.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new SchemeCoverageRule(
                (UUID) rs.getObject("id"), rule.schemeId(), rule.serviceCategory(), rule.serviceItemId(),
                rule.coverageType(), rule.coverageValue(), rule.requiresPreauth(), rule.effectiveFrom(), null,
                rule.createdBy(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public void close(UUID ruleId, LocalDate effectiveTo) {
        jdbcTemplate.update("UPDATE scheme_coverage_rule SET effective_to = :effectiveTo WHERE id = :id",
                new MapSqlParameterSource().addValue("id", ruleId).addValue("effectiveTo", effectiveTo));
    }

    @Override
    public Optional<SchemeCoverageRule> findApplicable(UUID schemeId, UUID serviceItemId, ServiceCategory serviceCategory,
                                                         LocalDate serviceDate) {
        // service_item_id IS NOT NULL DESC: an item-specific rule always
        // wins over a category rule covering the same date, resolved as a
        // data ordering (same technique as
        // ProviderBillingCategoryRepository.findHighestPriorityCurrent)
        // rather than a code-level judgement call.
        String sql = SELECT_COLUMNS + """
                WHERE scheme_id = :schemeId
                  AND effective_from <= :serviceDate
                  AND (effective_to IS NULL OR effective_to >= :serviceDate)
                  AND (service_item_id = :serviceItemId
                       OR (service_item_id IS NULL AND service_category = :serviceCategory))
                ORDER BY service_item_id IS NOT NULL DESC
                LIMIT 1
                """;
        List<SchemeCoverageRule> results = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("schemeId", schemeId)
                .addValue("serviceItemId", serviceItemId)
                .addValue("serviceCategory", serviceCategory.name())
                .addValue("serviceDate", serviceDate), this::mapRow);
        return results.stream().findFirst();
    }

    private SchemeCoverageRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        String serviceCategory = rs.getString("service_category");
        Object serviceItemId = rs.getObject("service_item_id");
        Date effectiveTo = rs.getDate("effective_to");
        BigDecimal coverageValue = rs.getBigDecimal("coverage_value");
        return new SchemeCoverageRule(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("scheme_id"),
                serviceCategory == null ? null : ServiceCategory.valueOf(serviceCategory),
                serviceItemId == null ? null : (UUID) serviceItemId,
                CoverageType.valueOf(rs.getString("coverage_type")),
                coverageValue,
                rs.getBoolean("requires_preauth"),
                rs.getDate("effective_from").toLocalDate(),
                effectiveTo == null ? null : effectiveTo.toLocalDate(),
                (UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
