package com.adags.shared.authz.infrastructure;

import com.adags.shared.authz.ScopeHierarchyPort;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcScopeHierarchyPort implements ScopeHierarchyPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcScopeHierarchyPort(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ScopeRef> parentOf(ScopeRef scope) {
        return switch (scope.type()) {
            case WARD -> lookupParent("SELECT department_id FROM ward WHERE id = :id", scope.id(), ScopeType.DEPARTMENT);
            case DEPARTMENT -> lookupParent("SELECT facility_id FROM department WHERE id = :id", scope.id(), ScopeType.FACILITY);
            case STORE -> lookupParent("SELECT facility_id FROM store WHERE id = :id", scope.id(), ScopeType.FACILITY);
            case FACILITY, GLOBAL, CLINIC -> Optional.empty();
        };
    }

    private Optional<ScopeRef> lookupParent(String sql, UUID id, ScopeType parentType) {
        List<UUID> parentIds = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                (rs, rowNum) -> (UUID) rs.getObject(1)
        );
        return parentIds.stream().findFirst().map(parentId -> new ScopeRef(parentType, parentId));
    }
}
