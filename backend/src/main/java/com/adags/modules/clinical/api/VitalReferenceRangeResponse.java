package com.adags.modules.clinical.api;

import com.adags.modules.clinical.domain.AgeBand;
import com.adags.modules.clinical.domain.Sex;
import com.adags.modules.clinical.domain.VitalReferenceRange;
import com.adags.modules.clinical.domain.VitalType;

public record VitalReferenceRangeResponse(
        VitalType vitalType,
        AgeBand ageBand,
        Sex sex,
        Double implausibleLow,
        Double implausibleHigh,
        Double abnormalLow,
        Double abnormalHigh
) {
    public static VitalReferenceRangeResponse from(VitalReferenceRange range) {
        return new VitalReferenceRangeResponse(
                range.vitalType(), range.ageBand(), range.sex(),
                range.implausibleLow(), range.implausibleHigh(), range.abnormalLow(), range.abnormalHigh());
    }
}
