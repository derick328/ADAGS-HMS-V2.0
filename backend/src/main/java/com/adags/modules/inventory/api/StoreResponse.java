package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Store;

import java.util.UUID;

public record StoreResponse(UUID id, String name, String type, UUID facilityId) {
    public static StoreResponse from(Store store) {
        return new StoreResponse(store.id(), store.name(), store.type(), store.facilityId());
    }
}
