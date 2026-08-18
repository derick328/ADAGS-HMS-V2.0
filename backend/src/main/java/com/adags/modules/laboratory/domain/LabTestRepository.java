package com.adags.modules.laboratory.domain;

import java.util.Optional;
import java.util.UUID;

public interface LabTestRepository {

    LabTest insert(LabTest test);

    Optional<LabTest> findById(UUID id);

    Optional<LabTest> findByCode(String code);
}
