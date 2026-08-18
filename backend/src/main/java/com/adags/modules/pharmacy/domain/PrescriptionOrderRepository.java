package com.adags.modules.pharmacy.domain;

import java.util.Optional;
import java.util.UUID;

public interface PrescriptionOrderRepository {

    PrescriptionOrder insert(PrescriptionOrder order);

    Optional<PrescriptionOrder> findById(UUID id);
}
