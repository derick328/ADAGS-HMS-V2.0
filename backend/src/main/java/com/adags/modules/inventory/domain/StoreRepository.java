package com.adags.modules.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

    Store insert(Store store);

    Optional<Store> findById(UUID id);
}
