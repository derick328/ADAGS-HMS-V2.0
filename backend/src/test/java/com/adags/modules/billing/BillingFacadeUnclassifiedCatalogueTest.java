package com.adags.modules.billing;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.billing.application.CatalogItemLookupPort;
import com.adags.modules.billing.application.CatalogRow;
import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Option 2 fanout: BillingFacade surfaces active catalogue rows the
// admin importer landed as TYPE=Pharmacy / TYPE=Laboratory so the
// prescription and lab-order typeaheads can render them as
// visible-but-unselectable placeholders. Codes already classified in
// the caller's own module are excluded so the two lists never collide.
class BillingFacadeUnclassifiedCatalogueTest extends IntegrationTestSupport {

    @Autowired
    private BillingFacade billingFacade;

    @Autowired
    private CatalogItemLookupPort catalogItemLookup;

    @Test
    void returnsUnclassifiedPharmacyRowsMatchingQueryAndExcludesClassifiedCodes() {
        String suite = "TEST" + Integer.toHexString(UUID.randomUUID().hashCode()).toUpperCase();
        String keptCode = suite + "-KEPT";
        String excludedCode = suite + "-EXCLUDED";
        catalogItemLookup.insertNewCatalogItem(newPharmacyRow(keptCode, suite + " Kept Drug"));
        catalogItemLookup.insertNewCatalogItem(newPharmacyRow(excludedCode, suite + " Excluded Drug"));

        List<BillingFacade.UnclassifiedCatalogueEntry> result = billingFacade.findUnclassifiedCatalogueEntries(
                BillingFacade.CatalogueKind.PHARMACY, suite, Set.of(excludedCode), 20);

        assertThat(result)
                .extracting(BillingFacade.UnclassifiedCatalogueEntry::code)
                .contains(keptCode)
                .doesNotContain(excludedCode);
        assertThat(result)
                .extracting(BillingFacade.UnclassifiedCatalogueEntry::name)
                .contains(suite + " Kept Drug");
    }

    @Test
    void doesNotReturnRowsOfADifferentType() {
        String suite = "TEST" + Integer.toHexString(UUID.randomUUID().hashCode()).toUpperCase();
        catalogItemLookup.insertNewCatalogItem(newPharmacyRow(suite + "-DRG", suite + " Pharmacy Row"));
        catalogItemLookup.insertNewCatalogItem(newLabRow(suite + "-LAB", suite + " Lab Row"));

        List<BillingFacade.UnclassifiedCatalogueEntry> pharmacyResults = billingFacade.findUnclassifiedCatalogueEntries(
                BillingFacade.CatalogueKind.PHARMACY, suite, Set.of(), 20);
        List<BillingFacade.UnclassifiedCatalogueEntry> labResults = billingFacade.findUnclassifiedCatalogueEntries(
                BillingFacade.CatalogueKind.LABORATORY, suite, Set.of(), 20);

        assertThat(pharmacyResults).extracting(BillingFacade.UnclassifiedCatalogueEntry::code)
                .contains(suite + "-DRG").doesNotContain(suite + "-LAB");
        assertThat(labResults).extracting(BillingFacade.UnclassifiedCatalogueEntry::code)
                .contains(suite + "-LAB").doesNotContain(suite + "-DRG");
    }

    private static CatalogRow newPharmacyRow(String code, String name) {
        return new CatalogRow(1, null, code, code, name, null,
                CatalogItemType.PHARMACY, CatalogBillingCategory.MEDICINES_AND_CONSUMABLES, 1000L);
    }

    private static CatalogRow newLabRow(String code, String name) {
        return new CatalogRow(1, null, code, code, name, null,
                CatalogItemType.LABORATORY, CatalogBillingCategory.DIAGNOSTIC_EXAMINATIONS, 5000L);
    }
}
