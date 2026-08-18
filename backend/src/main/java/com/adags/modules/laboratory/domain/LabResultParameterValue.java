package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.UUID;

// 15-lab-technician.md §5.1/§5.2: one row per generated parameter field.
// appliedRangeId snapshots which LabReferenceRange produced flag — a later
// retune must not rewrite what was flagged historically.
public record LabResultParameterValue(
        UUID id,
        UUID resultId,
        UUID parameterId,
        double resultValue,
        ResultFlag flag,
        boolean flagOverridden,
        String overrideReason,
        UUID appliedRangeId,
        Instant createdAt
) {

    public static LabResultParameterValue create(UUID resultId, UUID parameterId, double resultValue,
                                                  ResultFlag flag, boolean flagOverridden, String overrideReason,
                                                  UUID appliedRangeId) {
        return new LabResultParameterValue(
                null, resultId, parameterId, resultValue, flag, flagOverridden, overrideReason, appliedRangeId, null);
    }
}
