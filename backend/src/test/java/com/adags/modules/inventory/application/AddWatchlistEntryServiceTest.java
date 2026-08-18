package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.NarcoticWatchlistEntry;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddWatchlistEntryServiceTest extends IntegrationTestSupport {

    @Autowired
    private AddWatchlistEntryService addWatchlistEntryService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE narcotic_watchlist_entry", new MapSqlParameterSource());
    }

    @Test
    void addsAnEntryWithItsSource() {
        NarcoticWatchlistEntry entry = addWatchlistEntryService.add(new AddWatchlistEntryCommand("Pethidine",
                "N02AB", "TMDA Scheduling of Medicines Regulations 2025, First Schedule", LocalDate.of(2025, 1, 1),
                bootstrapAdminId()));

        assertThat(entry.id()).isNotNull();
        assertThat(entry.innName()).isEqualTo("Pethidine");
        assertThat(entry.sourceDocument()).contains("First Schedule");
    }

    @Test
    void rejectsAnEntryWithNoSourceDocument() {
        assertThatThrownBy(() -> addWatchlistEntryService.add(new AddWatchlistEntryCommand("Pethidine", null,
                " ", LocalDate.now(), bootstrapAdminId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
