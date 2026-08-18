package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.UUID;

// 15-lab-technician.md §5.1: one row per reportable value on a test (e.g.
// "HGB" on a full blood count). The phase-5 result form generates one
// input per parameter, ordered by sortOrder — the technician never types
// name/unit/range.
public record LabTestParameter(
        UUID id,
        UUID testId,
        String code,
        String name,
        String unit,
        int sortOrder,
        String method,
        String loincCode,
        Instant createdAt
) {

    public static LabTestParameter create(UUID testId, String code, String name, String unit, int sortOrder,
                                           String method, String loincCode) {
        return new LabTestParameter(null, testId, code, name, unit, sortOrder, method, loincCode, null);
    }
}
