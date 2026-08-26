package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabTestRepository {

    LabTest insert(LabTest test);

    Optional<LabTest> findById(UUID id);

    Optional<LabTest> findByCode(String code);

    /** 15-lab-technician.md §5.1: the catalogue the Result Entry form generates from, active tests only, name order. */
    List<LabTest> findAllActive();
}
