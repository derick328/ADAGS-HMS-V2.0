package com.adags.modules.pharmacy.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionOrderRepository {

    PrescriptionOrder insert(PrescriptionOrder order);

    Optional<PrescriptionOrder> findById(UUID id);

    /** 08-doctor-navigation.md §5: "Request History" — every order this doctor prescribed, newest first. */
    List<PrescriptionOrder> findByPrescribedBy(UUID prescribedBy);

    /** 08-doctor-navigation.md §3.3: every order on this encounter, across every prescriber, newest first. */
    List<PrescriptionOrder> findByEncounterId(UUID encounterId);
}
