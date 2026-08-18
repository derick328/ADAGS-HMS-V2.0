package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecimenRepository {

    Specimen insert(Specimen specimen);

    Optional<Specimen> findById(UUID id);

    /** All specimens ever collected against this order, most recent first — recollection appends, never replaces. */
    List<Specimen> findByLabOrderId(UUID labOrderId);
}
