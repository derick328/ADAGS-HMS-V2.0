package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;

import java.util.UUID;

public record ItemResponse(
        UUID id,
        String code,
        String name,
        String classification,
        String form,
        String strength,
        String unit,
        RegulatorySchedule regulatorySchedule,
        int reorderLevel,
        boolean active,
        boolean requiresAdministrationWitness
) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(item.id(), item.code(), item.name(), item.classification(), item.form(),
                item.strength(), item.unit(), item.regulatorySchedule(), item.reorderLevel(), item.active(),
                item.requiresAdministrationWitness());
    }
}
