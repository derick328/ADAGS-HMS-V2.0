package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 15-lab-technician.md §5.1/§5.3: age- and sex-banded bounds per parameter
// — what LabResultFlagEvaluator reads to compute N/H/L/C and reject a
// typo. validFrom/validTo: current-row versioning, same as PriceListEntry
// and VitalReferenceRange (V20__lab_catalogue.sql's partial unique index).
// implausibleLow/implausibleHigh are both non-null exactly when this band
// is usable for evaluation — see isConfigured() and
// V20__lab_catalogue.sql's comment for why they can't be critical_low/high.
public record LabReferenceRange(
        UUID id,
        UUID parameterId,
        int ageMinMonths,
        Integer ageMaxMonths,
        Sex sex,
        Double implausibleLow,
        Double implausibleHigh,
        Double normalLow,
        Double normalHigh,
        Double criticalLow,
        Double criticalHigh,
        LocalDate validFrom,
        LocalDate validTo,
        UUID createdBy,
        Instant createdAt
) {

    public static LabReferenceRange create(UUID parameterId, int ageMinMonths, Integer ageMaxMonths, Sex sex,
                                            Double implausibleLow, Double implausibleHigh,
                                            Double normalLow, Double normalHigh,
                                            Double criticalLow, Double criticalHigh, UUID createdBy) {
        return new LabReferenceRange(null, parameterId, ageMinMonths, ageMaxMonths, sex,
                implausibleLow, implausibleHigh, normalLow, normalHigh, criticalLow, criticalHigh,
                null, null, createdBy, null);
    }

    public boolean isConfigured() {
        return implausibleLow != null && implausibleHigh != null && normalLow != null && normalHigh != null;
    }
}
