package com.adags.modules.inpatient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BedRepository {

    Optional<Bed> findById(UUID id);

    List<Bed> findByWardId(UUID wardId);

    /** Beds in wardId with no active BedAssignment — status AVAILABLE only. */
    List<Bed> findFreeByWardId(UUID wardId);

    Bed insert(Bed bed);
}
