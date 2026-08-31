package com.adags.modules.billing.api;

import com.adags.modules.billing.application.CatalogItemView;

import java.util.UUID;

public record CatalogItemResponse(
        UUID id,
        String code,
        String originalCode,
        String name,
        String classification,
        String type,
        String billingCategory,
        Long currentCashPriceMinor,
        String currencyCode,
        boolean isActive
) {
    public static CatalogItemResponse from(CatalogItemView view) {
        return new CatalogItemResponse(
                view.serviceItemId(), view.code(), view.originalCode(), view.name(), view.classification(),
                view.type() == null ? null : view.type().name(),
                view.billingCategory() == null ? null : view.billingCategory().name(),
                view.currentCashPriceMinor(),
                "TZS",
                view.isActive());
    }
}
