package com.adags.modules.billing.domain;

import java.util.Optional;
import java.util.UUID;

public interface ServiceItemRepository {

    Optional<ServiceItem> findByCode(String code);

    Optional<ServiceItem> findById(UUID id);

    ServiceItem insert(ServiceItem item);
}
