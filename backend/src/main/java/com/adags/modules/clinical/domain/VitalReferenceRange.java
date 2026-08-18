package com.adags.modules.clinical.domain;

import java.time.LocalDate;
import java.util.UUID;

// 06-triage.md §2.2. implausible_low/implausible_high are both non-null
// exactly when this band has been configured (see V9__vital_reference_range.sql's
// comment) — abnormal_low/abnormal_high may each independently be null,
// which is a real "no bound on this side" clinical state, not a gap.
public record VitalReferenceRange(
        UUID id,
        VitalType vitalType,
        AgeBand ageBand,
        Sex sex,
        Double implausibleLow,
        Double implausibleHigh,
        Double abnormalLow,
        Double abnormalHigh,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
    public boolean isConfigured() {
        return implausibleLow != null && implausibleHigh != null;
    }
}
