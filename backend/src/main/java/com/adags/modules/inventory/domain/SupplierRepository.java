package com.adags.modules.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository {

    Supplier insert(Supplier supplier);

    Optional<Supplier> findById(UUID id);

    /** 12-pharmacist.md §7.2: the restock form's "managed list, not free text" supplier picker. */
    List<Supplier> findAllActive();

    /** Dedupes an ad-hoc create from the restock form's "Other" branch — case-insensitive match on the trimmed name. */
    Optional<Supplier> findByNameIgnoreCase(String name);
}
