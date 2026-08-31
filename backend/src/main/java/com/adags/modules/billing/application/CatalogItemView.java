package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;

import java.util.UUID;

// Read-model returned by CatalogItemLookupPort. Not a domain aggregate —
// deliberately flat so the importer can diff a CatalogRow against the
// current row without touching ServiceItem's own constructor. Existing
// consumers of ServiceItem stay unchanged.
//
// `code` is the stable storage code (may carry a "#<externalRefId>"
// suffix on rows whose raw PRODUCT CODE collided in the source file
// during import); `originalCode` preserves the raw source value so the
// admin browse can group and search by what the spreadsheet said.
public record CatalogItemView(
        UUID serviceItemId,
        String code,
        String originalCode,
        String name,
        String classification,
        CatalogItemType type,
        CatalogBillingCategory billingCategory,
        Long currentCashPriceMinor,
        boolean isActive
) {
}
