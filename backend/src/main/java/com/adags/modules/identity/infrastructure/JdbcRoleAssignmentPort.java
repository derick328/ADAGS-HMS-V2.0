package com.adags.modules.identity.infrastructure;

import com.adags.shared.authz.RoleAssignmentPort;
import com.adags.shared.authz.RoleAssignmentView;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JdbcRoleAssignmentPort implements RoleAssignmentPort {

    private static final String ASSIGNMENTS_SQL = """
            SELECT ra.id, r.id AS role_id, r.code AS role_code, ra.scope_type, ra.scope_id,
                   ra.valid_from, ra.valid_to, ra.revoked_at
            FROM role_assignment ra
            JOIN role r ON r.id = ra.role_id
            WHERE ra.user_id = :userId
            """;

    private static final String PERMISSION_CODES_SQL = """
            SELECT p.code
            FROM role_permission rp
            JOIN permission p ON p.id = rp.permission_id
            WHERE rp.role_id = :roleId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRoleAssignmentPort(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RoleAssignmentView> findAssignments(UUID userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                ASSIGNMENTS_SQL, new MapSqlParameterSource("userId", userId));

        Map<UUID, Set<String>> permissionCodesByRole = new HashMap<>();

        return rows.stream()
                .map(row -> {
                    UUID roleId = (UUID) row.get("role_id");
                    Set<String> permissionCodes = permissionCodesByRole.computeIfAbsent(roleId, this::findPermissionCodes);
                    return toView(row, permissionCodes);
                })
                .toList();
    }

    private Set<String> findPermissionCodes(UUID roleId) {
        List<String> codes = jdbcTemplate.query(
                PERMISSION_CODES_SQL,
                new MapSqlParameterSource("roleId", roleId),
                (rs, rowNum) -> rs.getString("code"));
        return codes.stream().collect(Collectors.toUnmodifiableSet());
    }

    private RoleAssignmentView toView(Map<String, Object> row, Set<String> permissionCodes) {
        ScopeType scopeType = ScopeType.valueOf((String) row.get("scope_type"));
        UUID scopeId = (UUID) row.get("scope_id");
        return new RoleAssignmentView(
                (UUID) row.get("id"),
                (String) row.get("role_code"),
                new ScopeRef(scopeType, scopeId),
                toInstant(row.get("valid_from")),
                toInstant(row.get("valid_to")),
                toInstant(row.get("revoked_at")),
                permissionCodes
        );
    }

    private Instant toInstant(Object value) {
        return value == null ? null : ((Timestamp) value).toInstant();
    }
}
