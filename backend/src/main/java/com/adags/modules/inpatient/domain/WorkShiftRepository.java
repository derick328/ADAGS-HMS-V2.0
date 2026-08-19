package com.adags.modules.inpatient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkShiftRepository {

    WorkShift insert(WorkShift workShift);

    Optional<WorkShift> findById(UUID id);

    List<WorkShift> findActive();
}
