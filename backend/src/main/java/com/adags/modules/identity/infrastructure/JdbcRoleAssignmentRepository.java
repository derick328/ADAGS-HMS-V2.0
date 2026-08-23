package com.adags.modules.identity.infrastructure;

import com.adags.modules.identity.domain.RoleAssignmentRecord;
import com.adags.modules.identity.domain.RoleAssignmentRepository;
import com.adags.shared.authz.ScopeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRoleAssignmentRepository implements RoleAssignmentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT ra.id, ra.user_id, r.code AS role_code, ra.scope_type, ra.scope_id,
                   ra.valid_from, ra.valid_to, ra.revoked_at, ra.granted_by
            FROM role_assignment ra
            JOIN role r ON r.id = ra.role_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRoleAssignmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RoleAssignmentRecord grant(UUID userId, UUID roleId, ScopeType scopeType, UUID scopeId,
                                       Instant validFrom, Instant validTo, UUID grantedBy) {
        String sql = """
                INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id, valid_from, valid_to, granted_by)
                VALUES (:userId, :roleId, :scopeType, :scopeId, :validFrom, :validTo, :grantedBy)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("roleId", roleId)
                .addValue("scopeType", scopeType.name())
                .addValue("scopeId", scopeId, Types.OTHER)
                .addValue("validFrom", Timestamp.from(validFrom))
                .addValue("validTo", validTo == null ? null : Timestamp.from(validTo))
                .addValue("grantedBy", grantedBy, Types.OTHER);
        UUID id = jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> (UUID) rs.getObject("id"));
        return findById(id).orElseThrow();
    }

    @Override
    public List<RoleAssignmentRecord> findByUserId(UUID userId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE ra.user_id = :userId ORDER BY ra.created_at DESC",
                new MapSqlParameterSource("userId", userId),
                this::mapRow);
    }

    @Override
    public List<RoleAssignmentRecord> findActiveByRoleCode(String roleCode, Instant asOf) {
        String sql = SELECT_COLUMNS + """
                 WHERE r.code = :roleCode
                   AND ra.revoked_at IS NULL
                   AND ra.valid_from <= :asOf
                   AND (ra.valid_to IS NULL OR ra.valid_to > :asOf)
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("roleCode", roleCode)
                .addValue("asOf", Timestamp.from(asOf)), this::mapRow);
    }

    @Override
    public Optional<RoleAssignmentRecord> findById(UUID id) {
        List<RoleAssignmentRecord> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE ra.id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public void revoke(UUID id, Instant revokedAt) {
        jdbcTemplate.update(
                "UPDATE role_assignment SET revoked_at = :revokedAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("revokedAt", Timestamp.from(revokedAt))
                        .addValue("id", id));
    }

    private RoleAssignmentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp validTo = rs.getTimestamp("valid_to");
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new RoleAssignmentRecord(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("role_code"),
                ScopeType.valueOf(rs.getString("scope_type")),
                (UUID) rs.getObject("scope_id"),
                rs.getTimestamp("valid_from").toInstant(),
                validTo == null ? null : validTo.toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(),
                (UUID) rs.getObject("granted_by"));
    }
}
