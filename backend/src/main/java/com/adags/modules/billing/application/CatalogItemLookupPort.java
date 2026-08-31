package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogItemType;

import java.util.Collection;
import java.util.List;

// Bulk read + write port for the catalogue import's diff step. Kept
// separate from ServiceItemRepository so existing callers of that repo
// keep their narrow contract; the importer is the only surface that
// needs the extended columns (type, billing_category, classification,
// external_ref_id) plus the current CASH price alongside.
public interface CatalogItemLookupPort {

    /** Returns each requested code that already exists — missing codes are simply absent. */
    List<CatalogItemView> findByCodes(List<String> codes);

    /** Inserts a new service_item row with the catalogue-import metadata populated. Returns the new UUID. */
    java.util.UUID insertNewCatalogItem(CatalogRow row);

    /**
     * Updates the metadata columns only — name, type, billing_category,
     * classification, external_ref_id. Never touches 'category' (existing
     * consumers key off it) and never touches price (price goes through
     * PriceListEntryRepository's close-then-insert path).
     */
    void updateMetadata(java.util.UUID serviceItemId, CatalogRow row);

    /**
     * Filtered list for the admin "View catalogue items" panel. Only
     * activeOnly = true is exposed on the main list; the archive view
     * (soft-deactivated items) is a separate concern.
     */
    List<CatalogItemView> listItems(CatalogItemQuery query);

    /** Total matching count for pagination. */
    int countItems(CatalogItemQuery query);

    /**
     * Root CLAUDE.md "Data": never a hard delete of a financial reference
     * — is_active = false is the soft-delete signal on service_item.
     * Returns the count of rows deactivated (rows already inactive are
     * skipped, hence idempotent).
     */
    int deactivateAllActive();

    /**
     * Active catalogue rows whose Excel TYPE matches {@code type} and
     * whose code is not in {@code excludeCodes}, matched by name / code /
     * original_code against {@code query} (blank query matches
     * everything). Used by BillingFacade#findUnclassifiedCatalogueEntries
     * to give the prescription and lab-order typeaheads visibility of
     * imported rows the pharmacy / lab modules have not classified yet.
     */
    List<CatalogItemView> findUnclassified(
            CatalogItemType type, String query, Collection<String> excludeCodes, int limit);
}
