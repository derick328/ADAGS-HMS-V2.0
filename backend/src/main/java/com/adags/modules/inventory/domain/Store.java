package com.adags.modules.inventory.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §12/§8: a physical stock-holding location — main
// pharmacy, a lab sub-store, a ward sub-store. Carries facility_id since a
// store is a place (root CLAUDE.md "Facility"), unlike Item or Supplier.
public record Store(UUID id, String name, String type, UUID facilityId, boolean active, Instant createdAt) {

    public static Store create(String name, String type, UUID facilityId) {
        return new Store(null, name, type, facilityId, true, null);
    }
}
