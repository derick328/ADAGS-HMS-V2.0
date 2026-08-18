package com.adags.modules.pharmacy.api;

import com.adags.shared.kernel.ItemSummary;

import java.util.UUID;

public record ItemSummaryResponse(UUID itemId, String code, String name, String unit) {

    public static ItemSummaryResponse from(ItemSummary summary) {
        return new ItemSummaryResponse(summary.itemId(), summary.code(), summary.name(), summary.unit());
    }
}
