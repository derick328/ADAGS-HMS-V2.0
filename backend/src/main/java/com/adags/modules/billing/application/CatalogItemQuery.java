package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;

// Filter for the admin "View catalogue items" panel. query is applied
// against PRODUCT NAME with the trigram index (the primary search vector
// per the user spec); type / billingCategory narrow to one bucket.
// activeOnly defaults true — a separate flag surfaces inactive rows when
// the admin wants to audit what was deactivated.
public record CatalogItemQuery(
        String query,
        CatalogItemType type,
        CatalogBillingCategory billingCategory,
        boolean activeOnly,
        int page,
        int pageSize
) {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 500;

    public CatalogItemQuery {
        page = Math.max(0, page);
        pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize == 0 ? DEFAULT_PAGE_SIZE : pageSize));
    }
}
