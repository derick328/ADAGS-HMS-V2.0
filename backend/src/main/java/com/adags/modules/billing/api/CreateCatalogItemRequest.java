package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;

public record CreateCatalogItemRequest(
        String name,
        CatalogItemType type,
        CatalogBillingCategory billingCategory,
        String classification,
        String productCodePrefix,
        Long priceMinor
) {}
