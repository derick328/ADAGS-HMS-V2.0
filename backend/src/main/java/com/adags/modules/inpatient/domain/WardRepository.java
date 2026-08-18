package com.adags.modules.inpatient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WardRepository {

    Optional<Ward> findById(UUID id);

    List<Ward> findByFacilityId(UUID facilityId);

    Ward insert(Ward ward);
}
