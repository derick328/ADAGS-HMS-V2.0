package com.adags.modules.billing;

import com.adags.modules.billing.application.CreateCatalogItemService;
import com.adags.modules.billing.application.UpdateCatalogItemService;
import com.adags.modules.billing.application.CatalogPriceManagementService;
import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Transactional
class CatalogItemEditingTest extends IntegrationTestSupport {
    @Autowired CreateCatalogItemService create;
    @Autowired UpdateCatalogItemService update;
    @Autowired CatalogPriceManagementService prices;
    @Autowired NamedParameterJdbcTemplate jdbc;

    private UUID actor() {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }

    @Test
    void createsUnpricedItemThenEditsMetadataAndAddsPriceAndRejectsStaleSave() {
        UUID actor = actor();
        var created = create.create(new CreateCatalogItemService.Command("New test", CatalogItemType.LABORATORY,
                CatalogBillingCategory.DIAGNOSTIC_EXAMINATIONS, "Chemistry", "LAB", null), actor);
        assertThat(prices.history(created.id())).isEmpty();
        var original = new UpdateCatalogItemService.Details("New test", CatalogItemType.LABORATORY,
                CatalogBillingCategory.DIAGNOSTIC_EXAMINATIONS, "Chemistry", true, null);
        var next = new UpdateCatalogItemService.Details("Renamed test", CatalogItemType.LABORATORY,
                CatalogBillingCategory.OTHER_CHARGES, "Updated classification", false, 1500L);
        update.update(created.id(), new UpdateCatalogItemService.Command(original, next), actor);
        var params = new MapSqlParameterSource("id", created.id()).addValue("code", created.productCode());
        assertThat(jdbc.queryForObject("SELECT name FROM service_item WHERE id = :id", params, String.class)).isEqualTo("Renamed test");
        assertThat(jdbc.queryForObject("SELECT name FROM lab_test WHERE code = :code", params, String.class)).isEqualTo("Renamed test");
        assertThat(jdbc.queryForObject("SELECT is_active FROM service_item WHERE id = :id", params, Boolean.class)).isFalse();
        assertThat(prices.history(created.id())).extracting(CatalogPriceManagementService.PriceChange::priceMinor).containsExactly(1500L);
        assertThatThrownBy(() -> update.update(created.id(), new UpdateCatalogItemService.Command(original, next), actor))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test
    void distinguishesZeroFromMissingPriceAndVersionsPriceRemoval() {
        UUID actor = actor();
        var created = create.create(new CreateCatalogItemService.Command("Zero price", CatalogItemType.PROCEDURE,
                CatalogBillingCategory.PROCEDURAL_CHARGES, null, "PRO", 0L), actor);
        assertThat(prices.history(created.id())).extracting(CatalogPriceManagementService.PriceChange::priceMinor).containsExactly(0L);
        var original = new UpdateCatalogItemService.Details("Zero price", CatalogItemType.PROCEDURE,
                CatalogBillingCategory.PROCEDURAL_CHARGES, null, true, 0L);
        var next = new UpdateCatalogItemService.Details("Zero price", CatalogItemType.PROCEDURE,
                CatalogBillingCategory.PROCEDURAL_CHARGES, null, true, null);
        update.update(created.id(), new UpdateCatalogItemService.Command(original, next), actor);
        assertThat(prices.history(created.id())).hasSize(1);
        assertThat(prices.history(created.id()).get(0).validTo()).isNotNull();
    }
    @Test
    void rejectsNegativeCreationPrices() {
        assertThatThrownBy(() -> create.create(new CreateCatalogItemService.Command("Invalid", CatalogItemType.PROCEDURE,
                CatalogBillingCategory.PROCEDURAL_CHARGES, null, "PRO", -1L), actor()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    }
    @Test
    void priceOnlyEditPreservesExistingPharmacyClassification() {
        UUID actor = actor();
        var created = create.create(new CreateCatalogItemService.Command("Medicine", CatalogItemType.PHARMACY,
                CatalogBillingCategory.MEDICINES_AND_CONSUMABLES, null, "DRG", null), actor);
        var params = new MapSqlParameterSource("code", created.productCode());
        jdbc.update("UPDATE item SET classification = 'Antibiotic' WHERE code = :code", params);
        var original = new UpdateCatalogItemService.Details("Medicine", CatalogItemType.PHARMACY,
                CatalogBillingCategory.MEDICINES_AND_CONSUMABLES, null, true, null);
        var next = new UpdateCatalogItemService.Details("Medicine", CatalogItemType.PHARMACY,
                CatalogBillingCategory.MEDICINES_AND_CONSUMABLES, null, true, 500L);
        update.update(created.id(), new UpdateCatalogItemService.Command(original, next), actor);
        assertThat(jdbc.queryForObject("SELECT classification FROM item WHERE code = :code", params, String.class))
                .isEqualTo("Antibiotic");
    }
}
