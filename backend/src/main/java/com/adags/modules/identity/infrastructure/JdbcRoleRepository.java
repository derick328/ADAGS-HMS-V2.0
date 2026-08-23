package com.adags.modules.identity.infrastructure;

import com.adags.modules.identity.domain.RoleRepository;
import com.adags.modules.identity.domain.RoleSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRoleRepository implements RoleRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRoleRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RoleSummary> findAll() {
        return jdbcTemplate.query(
                "SELECT id, code, name FROM role ORDER BY name",
                (rs, rowNum) -> new RoleSummary((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name")));
    }

    @Override
    public Optional<RoleSummary> findByCode(String code) {
        List<RoleSummary> results = jdbcTemplate.query(
                "SELECT id, code, name FROM role WHERE code = :code",
                new MapSqlParameterSource("code", code),
                (rs, rowNum) -> new RoleSummary((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name")));
        return results.stream().findFirst();
    }
}
