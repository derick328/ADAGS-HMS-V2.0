package com.adags.shared.settings;

import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A missing/wiped protocol_setting row must be a loud failure for a caller
// that treats the key as always-seeded — a fallback that happens to match
// the seed is never exercised, which is exactly how the TRUNCATE ...
// department CASCADE bug (wiping protocol_setting via its FK) went
// unnoticed across fifteen test files.
class JdbcProtocolSettingReaderTest extends IntegrationTestSupport {

    private static final String KEY = "patients.duplicate_detection.low_similarity_threshold";

    @Autowired
    private ProtocolSettingReader reader;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
                        SELECT :key, '0.4'::jsonb, id FROM app_user ORDER BY created_at ASC LIMIT 1
                        ON CONFLICT DO NOTHING
                        """,
                new MapSqlParameterSource("key", KEY));
        jdbcTemplate.update(
                "UPDATE protocol_setting SET setting_value = '0.4'::jsonb WHERE setting_key = :key",
                new MapSqlParameterSource("key", KEY));
    }

    @Test
    void getDoubleReadsASeededRow() {
        assertThat(reader.getDouble(KEY)).isEqualTo(0.4);
    }

    @Test
    void getDoubleThrowsWhenTheRowIsMissing() {
        jdbcTemplate.update("DELETE FROM protocol_setting WHERE setting_key = :key",
                new MapSqlParameterSource("key", KEY));

        assertThatThrownBy(() -> reader.getDouble(KEY))
                .isInstanceOf(ProtocolSettingNotConfiguredException.class)
                .hasMessageContaining(KEY);
    }

    @Test
    void getDoubleWithDefaultFallsBackWhenTheRowIsMissing() {
        jdbcTemplate.update("DELETE FROM protocol_setting WHERE setting_key = :key",
                new MapSqlParameterSource("key", KEY));

        assertThat(reader.getDouble(KEY, 0.99)).isEqualTo(0.99);
    }

    @Test
    void getDoubleWithDefaultPrefersTheConfiguredRow() {
        assertThat(reader.getDouble(KEY, 0.99)).isEqualTo(0.4);
    }
}
