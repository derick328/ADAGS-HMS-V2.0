package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabReferenceRangeRepository {

    LabReferenceRange insert(LabReferenceRange range);

    /** All current (validTo IS NULL) rows for this parameter — the caller picks the band matching age/sex. */
    List<LabReferenceRange> findCurrentByParameterId(UUID parameterId);

    /**
     * 16-lab-verification-model.md v0.2 §7: "reference ranges as applied" —
     * a result display needs the exact snapshotted band
     * (LabResultParameterValue.appliedRangeId) regardless of whether it is
     * still current, since ranges can be retuned after a result was released.
     */
    Optional<LabReferenceRange> findById(UUID id);
}
