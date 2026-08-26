package com.adags.modules.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

    Store insert(Store store);

    Optional<Store> findById(UUID id);

    /** 15-lab-technician.md §5: resolves a facility's sub-store of a given type (e.g. "LAB") — at most one per (facility, type), enforced by a partial unique index (V64). */
    Optional<Store> findByFacilityIdAndType(UUID facilityId, String type);
}
