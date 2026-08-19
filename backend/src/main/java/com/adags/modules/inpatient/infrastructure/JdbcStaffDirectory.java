package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.StaffDirectory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStaffDirectory implements StaffDirectory {

    private static final String SQL = """
            SELECT ra.user_id
            FROM role_assignment ra
            JOIN role r ON r.id = ra.role_id
            WHERE r.code = :roleCode
              AND ra.revoked_at IS NULL
              AND (ra.valid_to IS NULL OR ra.valid_to > now())
              AND (ra.scope_type = 'GLOBAL' OR (ra.scope_type = 'FACILITY' AND ra.scope_id = :facilityId))
            ORDER BY ra.valid_from ASC
            LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStaffDirectory(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findOneActiveClinicalLead(UUID facilityId) {
        return findOneByRole("CLINICAL_LEAD", facilityId);
    }

    @Override
    public Optional<UUID> findOneActivePharmacist(UUID facilityId) {
        return findOneByRole("PHARMACIST", facilityId);
    }

    private Optional<UUID> findOneByRole(String roleCode, UUID facilityId) {
        List<UUID> results = jdbcTemplate.query(SQL, new MapSqlParameterSource()
                        .addValue("roleCode", roleCode)
                        .addValue("facilityId", facilityId),
                (rs, rowNum) -> (UUID) rs.getObject("user_id"));
        return results.stream().findFirst();
    }
}
