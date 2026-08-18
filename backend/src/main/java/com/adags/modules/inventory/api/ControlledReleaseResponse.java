package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.ItemClassificationChange;

import java.time.Instant;
import java.util.UUID;

public record ControlledReleaseResponse(UUID itemId, String reason, UUID changedBy, Instant changedAt) {

    public static ControlledReleaseResponse from(ItemClassificationChange change) {
        return new ControlledReleaseResponse(change.itemId(), change.reason(), change.changedBy(), change.changedAt());
    }
}
