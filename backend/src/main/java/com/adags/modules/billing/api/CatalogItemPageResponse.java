package com.adags.modules.billing.api;

import java.util.List;

public record CatalogItemPageResponse(
        int page,
        int pageSize,
        int totalCount,
        List<CatalogItemResponse> items
) {
}
