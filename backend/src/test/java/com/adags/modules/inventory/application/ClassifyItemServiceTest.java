package com.adags.modules.inventory.application;

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

// 12-pharmacist.md §10.6, 13-controlled-medicines-policy.md §2: every
// classification change is logged permanently with a reason.
class ClassifyItemServiceTest extends IntegrationTestSupport {

    @Autowired
    private CreateItemService createItemService;

    @Autowired
    private ActivateItemService activateItemService;

    @Autowired
    private ClassifyItemService classifyItemService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE item_classification_change, item CASCADE", new MapSqlParameterSource());
    }

    @Test
    void classifyingAnItemWritesAChangeLogRowWithTheReason() {
        Item item = createFixtureItem();

        Item classified = classifyItemService.classify(new ClassifyItemCommand(item.id(),
                RegulatorySchedule.PHARMACY_ONLY_OTC, "matched TMDA Third Schedule B", bootstrapAdminId()));

        assertThat(classified.regulatorySchedule()).isEqualTo(RegulatorySchedule.PHARMACY_ONLY_OTC);

        Integer count = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM item_classification_change
                        WHERE item_id = :itemId AND previous_schedule = 'UNCLASSIFIED'
                              AND new_schedule = 'PHARMACY_ONLY_OTC' AND reason = 'matched TMDA Third Schedule B'
                        """,
                new MapSqlParameterSource("itemId", item.id()), Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rejectsAClassificationWithNoReason() {
        Item item = createFixtureItem();

        assertThatThrownBy(() -> classifyItemService.classify(new ClassifyItemCommand(item.id(),
                RegulatorySchedule.PHARMACY_ONLY_OTC, "  ", bootstrapAdminId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Regression: reclassifying an already-active item to CONTROLLED must
    // not be rejected by trg_block_controlled_item_activation itself — the
    // item is deactivated in the same statement, so "active and CONTROLLED
    // never coexist" holds without the reclassification tripping its own
    // guard.
    @Test
    void reclassifyingAnActiveItemToControlledDeactivatesIt() {
        Item item = createFixtureItem();
        classifyItemService.classify(new ClassifyItemCommand(item.id(), RegulatorySchedule.PHARMACY_ONLY_OTC,
                "matched Third Schedule B", bootstrapAdminId()));
        Item activated = activateItemService.activate(item.id());
        assertThat(activated.active()).isTrue();

        Item reclassified = classifyItemService.classify(new ClassifyItemCommand(item.id(),
                RegulatorySchedule.CONTROLLED, "found on First Schedule narcotic list on review", bootstrapAdminId()));

        assertThat(reclassified.regulatorySchedule()).isEqualTo(RegulatorySchedule.CONTROLLED);
        assertThat(reclassified.active()).isFalse();
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
