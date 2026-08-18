package com.adags.modules.inventory.application;

import java.util.UUID;

public record CreateItemCommand(
        String code,
        String name,
        String classification,
        String form,
        String strength,
        String unit,
        int reorderLevel,
        UUID actorUserId
) {
}
