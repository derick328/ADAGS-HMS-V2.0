package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;

public record CreateCatalogItemRequest(
        String name,
        CatalogItemType type,
        CatalogBillingCategory billingCategory,
        String classification,
        String productCodePrefix,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true, minimum = "0") Long priceMinor
) {}
