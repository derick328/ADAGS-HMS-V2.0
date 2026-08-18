package com.adags.modules.laboratory.domain;

import java.util.Optional;
import java.util.UUID;

public interface LabOrderRepository {

    LabOrder insert(LabOrder order);

    Optional<LabOrder> findById(UUID id);
}
