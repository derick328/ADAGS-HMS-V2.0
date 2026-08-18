package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.UUID;

public interface LabResultParameterValueRepository {

    LabResultParameterValue insert(LabResultParameterValue value);

    List<LabResultParameterValue> findByResultId(UUID resultId);
}
