package com.adags.modules.surgery.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TheatreRepository {

    Theatre insert(Theatre theatre);

    Optional<Theatre> findById(UUID id);

    List<Theatre> findByFacilityId(UUID facilityId);
}
