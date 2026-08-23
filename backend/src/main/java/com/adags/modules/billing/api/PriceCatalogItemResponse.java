package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemCategory;

import java.util.UUID;

public record PriceCatalogItemResponse(
        UUID serviceItemId,
        String code,
        String name,
        ServiceItemCategory category,
        long priceMinor,
        String currencyCode
) {

    public static PriceCatalogItemResponse of(ServiceItem item, long priceMinor, String currencyCode) {
        return new PriceCatalogItemResponse(item.id(), item.code(), item.name(), item.category(), priceMinor, currencyCode);
    }
}
