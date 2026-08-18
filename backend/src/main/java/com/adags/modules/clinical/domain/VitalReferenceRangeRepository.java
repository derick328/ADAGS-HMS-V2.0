package com.adags.modules.clinical.domain;

import java.util.List;
import java.util.Optional;

public interface VitalReferenceRangeRepository {

    /** Prefers a sex-specific current row, falls back to the sex-agnostic ANY row. */
    Optional<VitalReferenceRange> findCurrent(VitalType vitalType, AgeBand ageBand, Sex sex);

    /** Current rows with no implausible bounds set — the startup-warning source. */
    List<VitalReferenceRange> findUnconfigured();
}
