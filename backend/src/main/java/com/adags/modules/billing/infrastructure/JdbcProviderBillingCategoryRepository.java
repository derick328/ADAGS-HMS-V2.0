package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.ProviderBillingCategory;
import com.adags.modules.billing.domain.ProviderBillingCategoryRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class JdbcProviderBillingCategoryRepository implements ProviderBillingCategoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProviderBillingCategoryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProviderBillingCategory> findHighestPriorityCurrent(Set<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, role_code, service_item_code, priority, effective_from, effective_to, created_by, created_at
                FROM provider_billing_category
                WHERE role_code IN (:roleCodes) AND effective_to IS NULL
                ORDER BY priority DESC
                LIMIT 1
                """;
        List<ProviderBillingCategory> results = jdbcTemplate.query(
                sql, new MapSqlParameterSource("roleCodes", roleCodes), this::mapRow);
        return results.stream().findFirst();
    }

    private ProviderBillingCategory mapRow(ResultSet rs, int rowNum) throws SQLException {
        Date validTo = rs.getDate("effective_to");
        return new ProviderBillingCategory(
                (java.util.UUID) rs.getObject("id"),
                rs.getString("role_code"),
                rs.getString("service_item_code"),
                rs.getInt("priority"),
                rs.getDate("effective_from").toLocalDate(),
                validTo == null ? null : validTo.toLocalDate(),
                (java.util.UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
