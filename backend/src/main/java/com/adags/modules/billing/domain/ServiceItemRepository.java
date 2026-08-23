package com.adags.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceItemRepository {

    Optional<ServiceItem> findByCode(String code);

    Optional<ServiceItem> findById(UUID id);

    ServiceItem insert(ServiceItem item);

    /** 02-billing-architecture.md §4.3 / 05-receptionist.md §7.2: the invoice line picker's typeahead — active items only. */
    List<ServiceItem> search(String query);
}
