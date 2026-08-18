package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.UUID;

public interface LabTestParameterRepository {

    LabTestParameter insert(LabTestParameter parameter);

    /** Ordered by sortOrder — the order the phase-5 result form renders. */
    List<LabTestParameter> findByTestId(UUID testId);
}
