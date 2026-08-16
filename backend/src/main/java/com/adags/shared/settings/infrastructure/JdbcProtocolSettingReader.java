package com.adags.shared.settings.infrastructure;

import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JdbcProtocolSettingReader implements ProtocolSettingReader {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProtocolSettingReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public double getDouble(String settingKey, double defaultValue) {
        // System-wide row only (department_id IS NULL) — no caller needs
        // department-scoped overrides yet; add a scope parameter here when
        // one does, rather than guessing the shape now.
        List<String> values = jdbcTemplate.query(
                "SELECT setting_value::text FROM protocol_setting WHERE setting_key = :key AND department_id IS NULL",
                new MapSqlParameterSource("key", settingKey),
                (rs, rowNum) -> rs.getString(1));
        return values.stream().findFirst().map(Double::parseDouble).orElse(defaultValue);
    }
}
