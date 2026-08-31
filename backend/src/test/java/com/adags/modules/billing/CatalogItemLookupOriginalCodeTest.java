package com.adags.modules.billing;

import com.adags.modules.billing.application.CatalogItemLookupPort;
import com.adags.modules.billing.application.CatalogItemQuery;
import com.adags.modules.billing.application.CatalogItemView;
import com.adags.modules.billing.application.CatalogRow;
import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// V68's original_code: three catalogue rows that share a raw PRODUCT
// CODE ("DIS0001") land as three distinct service_item rows via
// disambiguated effective codes, all traceable back through
// original_code, and the admin browse finds every one by searching
// either the effective code or the raw code.
class CatalogItemLookupOriginalCodeTest extends IntegrationTestSupport {

    @Autowired
    private CatalogItemLookupPort lookup;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void insertsThreeRowsSharingOneRawCodeAndAdminBrowseSurfacesAllOfThem() {
        // A code prefix unique to this test — the shared truncate rules
        // in this suite leave service_item alone (see
        // PriceListEntryRepositoryTest doc), so a distinctive prefix keeps
        // this test's fixture rows out of every other test's search.
        String suite = "DIS" + Integer.toHexString(UUID.randomUUID().hashCode()).toUpperCase();

        UUID first = lookup.insertNewCatalogItem(row(101, 4519, suite, suite, "Ankle Support L"));
        UUID second = lookup.insertNewCatalogItem(row(102, 4520, suite + "#4520", suite, "Ankle Support XL"));
        UUID third = lookup.insertNewCatalogItem(row(103, 4521, suite + "#4521", suite, "Knee Support XXL"));

        // Round-trip: every insert stored its own effective code and the
        // shared raw code alongside.
        List<CatalogItemView> byEffective = lookup.findByCodes(List.of(
                suite, suite + "#4520", suite + "#4521"));
        assertThat(byEffective)
                .extracting(CatalogItemView::originalCode)
                .containsOnly(suite);
        assertThat(byEffective)
                .extracting(CatalogItemView::serviceItemId)
                .containsExactlyInAnyOrder(first, second, third);

        // Admin browse: a search on the raw code returns all three,
        // proving the WHERE clause matches on original_code in addition
        // to code and name.
        List<CatalogItemView> browsed = lookup.listItems(new CatalogItemQuery(
                suite, null, null, true, 0, 20));
        assertThat(browsed)
                .extracting(CatalogItemView::name)
                .contains("Ankle Support L", "Ankle Support XL", "Knee Support XXL");

        // Sanity: the persisted rows carry the same original_code and
        // distinct effective codes.
        Integer distinctRawCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT original_code) FROM service_item WHERE original_code = :raw",
                new MapSqlParameterSource("raw", suite), Integer.class);
        assertThat(distinctRawCount).isEqualTo(1);

        Integer distinctEffectiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT code) FROM service_item WHERE original_code = :raw",
                new MapSqlParameterSource("raw", suite), Integer.class);
        assertThat(distinctEffectiveCount).isEqualTo(3);
    }

    private static CatalogRow row(int excelRow, Integer externalRefId,
                                  String effectiveCode, String originalCode, String name) {
        return new CatalogRow(
                excelRow, externalRefId, effectiveCode, originalCode, name, null,
                CatalogItemType.PHARMACY, CatalogBillingCategory.MEDICINES_AND_CONSUMABLES, 1000L);
    }
}
