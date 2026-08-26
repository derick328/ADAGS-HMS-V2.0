package com.adags.modules.inventory.application;

import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.inventory.domain.DuplicateItemCodeException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.shared.kernel.ItemSummary;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 12-pharmacist.md §9.1/§10.1: a new item is always UNCLASSIFIED and
// inactive — it becomes dispensable only through the classify/activate
// actions (build brief item 2), never at creation.
class CreateItemServiceTest extends IntegrationTestSupport {

    @Autowired
    private CreateItemService createItemService;

    @Autowired
    private InventoryFacade inventoryFacade;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE item CASCADE", new MapSqlParameterSource());
    }

    @Test
    void createsAnUnclassifiedInactiveItem() {
        Item item = createItemService.create(new CreateItemCommand(
                "AMX500", "Amoxicillin 500mg capsule", "ANTIBIOTIC", "CAPSULE", "500mg", "CAPSULE", 100,
                bootstrapAdminId()));

        assertThat(item.id()).isNotNull();
        assertThat(item.regulatorySchedule()).isEqualTo(RegulatorySchedule.UNCLASSIFIED);
        assertThat(item.active()).isFalse();
    }

    @Test
    void itemSummaryCarriesTheCatalogueClassificationAndForm() {
        Item item = createItemService.create(new CreateItemCommand(
                "PCM500", "Paracetamol 500mg tablet", "ANALGESIC", "TABLET", "500mg", "TABLET", 100,
                bootstrapAdminId()));

        ItemSummary summary = inventoryFacade.findItemSummary(item.id()).orElseThrow();

        assertThat(summary.classification()).isEqualTo("ANALGESIC");
        assertThat(summary.form()).isEqualTo("TABLET");
    }

    @Test
    void rejectsADuplicateCode() {
        createItemService.create(new CreateItemCommand(
                "AMX500", "Amoxicillin 500mg capsule", "ANTIBIOTIC", "CAPSULE", "500mg", "CAPSULE", 100,
                bootstrapAdminId()));

        assertThatThrownBy(() -> createItemService.create(new CreateItemCommand(
                "AMX500", "A different name", "ANTIBIOTIC", "CAPSULE", "500mg", "CAPSULE", 50,
                bootstrapAdminId())))
                .isInstanceOf(DuplicateItemCodeException.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
