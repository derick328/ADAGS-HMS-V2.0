package com.adags.modules.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository {

    Supplier insert(Supplier supplier);

    Optional<Supplier> findById(UUID id);
}
