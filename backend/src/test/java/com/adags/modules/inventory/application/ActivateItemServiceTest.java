package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.ControlledItemBlockedException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 14-controlled-medicines-tanzania.md §2/§3: CONTROLLED is blocked from
// catalogue activation.
class ActivateItemServiceTest extends IntegrationTestSupport {

    @Autowired
    private CreateItemService createItemService;

    @Autowired
    private ClassifyItemService classifyItemService;

    @Autowired
    private ActivateItemService activateItemService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE item_classification_change, item CASCADE", new MapSqlParameterSource());
    }

    @Test
    void activatesADispensableItem() {
        Item item = createFixtureItem();

        Item activated = activateItemService.activate(item.id());

        assertThat(activated.active()).isTrue();
    }

    @Test
    void rejectsActivatingAControlledItem() {
        Item item = createFixtureItem();
        classifyItemService.classify(new ClassifyItemCommand(item.id(), RegulatorySchedule.CONTROLLED,
                "on the First Schedule narcotic list", bootstrapAdminId()));

        assertThatThrownBy(() -> activateItemService.activate(item.id()))
                .isInstanceOf(ControlledItemBlockedException.class);
    }

    private Item createFixtureItem() {
        return createItemService.create(new CreateItemCommand("AMX500", "Amoxicillin 500mg capsule", "ANTIBIOTIC",
                "CAPSULE", "500mg", "CAPSULE", 100, bootstrapAdminId()));
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
