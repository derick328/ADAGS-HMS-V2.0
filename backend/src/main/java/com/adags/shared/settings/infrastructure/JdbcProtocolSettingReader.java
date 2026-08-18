package com.adags.shared.settings.infrastructure;

import com.adags.shared.settings.ProtocolSettingNotConfiguredException;
import com.adags.shared.settings.ProtocolSettingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JdbcProtocolSettingReader implements ProtocolSettingReader {

    private static final Logger log = LoggerFactory.getLogger(JdbcProtocolSettingReader.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProtocolSettingReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public double getDouble(String settingKey) {
        return find(settingKey)
                .map(Double::parseDouble)
                .orElseThrow(() -> new ProtocolSettingNotConfiguredException(settingKey));
    }

    @Override
    public double getDouble(String settingKey, double defaultValue) {
        Optional<String> value = find(settingKey);
        if (value.isEmpty()) {
            log.warn("protocol_setting '{}' has no system-wide row — falling back to default {}. "
                    + "If this key is expected to always be seeded, the caller should use getDouble(key) instead.",
                    settingKey, defaultValue);
            return defaultValue;
        }
        return Double.parseDouble(value.get());
    }

    private Optional<String> find(String settingKey) {
        // System-wide row only (department_id IS NULL) — no caller needs
        // department-scoped overrides yet; add a scope parameter here when
        // one does, rather than guessing the shape now.
        List<String> values = jdbcTemplate.query(
                "SELECT setting_value::text FROM protocol_setting WHERE setting_key = :key AND department_id IS NULL",
                new MapSqlParameterSource("key", settingKey),
                (rs, rowNum) -> rs.getString(1));
        return values.stream().findFirst();
    }
}
