package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.UUID;

public interface LabReferenceRangeRepository {

    LabReferenceRange insert(LabReferenceRange range);

    /** All current (validTo IS NULL) rows for this parameter — the caller picks the band matching age/sex. */
    List<LabReferenceRange> findCurrentByParameterId(UUID parameterId);
}
