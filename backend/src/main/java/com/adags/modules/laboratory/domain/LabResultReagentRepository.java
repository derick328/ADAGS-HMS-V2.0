package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.UUID;

public interface LabResultReagentRepository {

    LabResultReagent insert(LabResultReagent reagent);

    List<LabResultReagent> findByResultId(UUID resultId);
}
