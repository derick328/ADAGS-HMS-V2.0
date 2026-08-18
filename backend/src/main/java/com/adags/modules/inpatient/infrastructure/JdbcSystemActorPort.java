package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.SystemActorPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JdbcSystemActorPort implements SystemActorPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSystemActorPort(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID systemActorId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user ORDER BY created_at ASC LIMIT 1", new MapSqlParameterSource(), UUID.class);
    }
}
