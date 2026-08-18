package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.ClinicalLeadDirectory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcClinicalLeadDirectory implements ClinicalLeadDirectory {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcClinicalLeadDirectory(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findOneActiveClinicalLead(UUID facilityId) {
        String sql = """
                SELECT ra.user_id
                FROM role_assignment ra
                JOIN role r ON r.id = ra.role_id
                WHERE r.code = 'CLINICAL_LEAD'
                  AND ra.revoked_at IS NULL
                  AND (ra.valid_to IS NULL OR ra.valid_to > now())
                  AND (ra.scope_type = 'GLOBAL' OR (ra.scope_type = 'FACILITY' AND ra.scope_id = :facilityId))
                ORDER BY ra.valid_from ASC
                LIMIT 1
                """;
        List<UUID> results = jdbcTemplate.query(
                sql, new MapSqlParameterSource("facilityId", facilityId),
                (rs, rowNum) -> (UUID) rs.getObject("user_id"));
        return results.stream().findFirst();
    }
}
