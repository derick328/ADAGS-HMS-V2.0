package com.adags.modules.inpatient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftHandoverRepository {

    ShiftHandover insert(ShiftHandover handover);

    Optional<ShiftHandover> findById(UUID id);

    List<ShiftHandover> findByAdmissionId(UUID admissionId);

    /** Conditional UPDATE ... WHERE acknowledged_at IS NULL — false if already acknowledged. */
    boolean acknowledge(UUID handoverId, UUID acknowledgedBy);
}
