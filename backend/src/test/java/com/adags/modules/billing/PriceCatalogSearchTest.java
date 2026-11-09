package com.adags.modules.billing;

import com.adags.modules.billing.application.PriceResolutionService;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemCategory;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.modules.billing.infrastructure.JdbcCatalogItemLookup;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 02-billing-architecture.md §4.3 ⚠12: the invoice line picker's typeahead
// — the search itself (ServiceItemRepository.search) and the "no price for
// this payer -> excluded, not zero" rule PriceCatalogController applies on
// top of it are tested at this layer, since PriceCatalogController itself
// is a thin composition of the two.
class PriceCatalogSearchTest extends IntegrationTestSupport {

    @Autowired
    private ServiceItemRepository serviceItemRepository;
    @Autowired
    private PriceResolutionService priceResolutionService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    private JdbcCatalogItemLookup catalogItemLookup;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        DELETE FROM price_list_entry
                        WHERE service_item_id IN (SELECT id FROM service_item WHERE code LIKE 'TEST-CATALOG%')
                        """, new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM service_item WHERE code LIKE 'TEST-CATALOG%'", new MapSqlParameterSource());
    }

    @Test
    void searchMatchesByNameOrCodeAmongActiveItemsOnly() {
        String code = "TEST-CATALOG-" + UUID.randomUUID().toString().substring(0, 8);
        serviceItemRepository.insert(new ServiceItem(null, code, "Wound dressing kit", ServiceItemCategory.PROCEDURE, true));
        String inactiveCode = "TEST-CATALOG-" + UUID.randomUUID().toString().substring(0, 8);
        serviceItemRepository.insert(new ServiceItem(null, inactiveCode, "Wound dressing old kit", ServiceItemCategory.PROCEDURE, false));

        List<ServiceItem> results = serviceItemRepository.search("wound dressing");

        assertThat(results).extracting(ServiceItem::code).contains(code).doesNotContain(inactiveCode);
    }

    @Test
    void anItemWithNoPriceForThePayerIsExcludedFromCatalogueResults() {
        String priced = "TEST-CATALOG-" + UUID.randomUUID().toString().substring(0, 8);
        ServiceItem pricedItem = serviceItemRepository.insert(new ServiceItem(null, priced, "Catalog priced item", ServiceItemCategory.PROCEDURE, true));
        UUID priceListId = jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = 'CASH'", new MapSqlParameterSource(), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, created_by)
                        VALUES (:priceListId, :serviceItemId, 1500, 'TZS', (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource().addValue("priceListId", priceListId).addValue("serviceItemId", pricedItem.id()));

        String unpriced = "TEST-CATALOG-" + UUID.randomUUID().toString().substring(0, 8);
        serviceItemRepository.insert(new ServiceItem(null, unpriced, "Catalog unpriced item", ServiceItemCategory.PROCEDURE, true));

        // Same logic PriceCatalogController.search applies per result — this
        // proves the resolution behaviour the controller relies on: the
        // priced item resolves, the unpriced one throws and would be
        // dropped rather than returned with a null/zero price.
        assertThat(priceResolutionService.resolvePrice(priced, PayerCategory.CASH).amountMinor()).isEqualTo(1500);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> priceResolutionService.resolvePrice(unpriced, PayerCategory.CASH))
                .isInstanceOf(com.adags.modules.billing.domain.PriceNotConfiguredException.class);
    }

    @Test
    @Transactional
    void catalogueResetKeepsTriageConsultationItemsAndPricesActive() {
        catalogItemLookup.deactivateAllActive();

        assertThat(serviceItemRepository.findByCode("CONSULT_CLINICAL_OFFICER").orElseThrow().isActive()).isTrue();
        assertThat(serviceItemRepository.findByCode("CONSULT_GENERAL").orElseThrow().isActive()).isTrue();
        assertThat(serviceItemRepository.findByCode("CONSULT_SPECIALIST").orElseThrow().isActive()).isTrue();
        assertThat(priceResolutionService.resolvePrice("CONSULT_CLINICAL_OFFICER", PayerCategory.CASH).amountMinor()).isEqualTo(5000);
        assertThat(priceResolutionService.resolvePrice("CONSULT_GENERAL", PayerCategory.CASH).amountMinor()).isEqualTo(8000);
        assertThat(priceResolutionService.resolvePrice("CONSULT_SPECIALIST", PayerCategory.CASH).amountMinor()).isEqualTo(30000);
        assertThat(priceResolutionService.resolvePrice("CONSULT_GENERAL", PayerCategory.NHIF).amountMinor()).isEqualTo(8000);
        assertThat(priceResolutionService.resolvePrice("CONSULT_SPECIALIST", PayerCategory.NHIF).amountMinor()).isEqualTo(30000);
    }
}
