package com.adags.modules.referencedata.domain;

import java.time.Instant;
import java.util.UUID;

// 10-clinical-reference-data.md §2.2. departmentId null means hospital-wide.
// Resolution order at search time: this department's common codes, then
// hospital-wide, then full active/billable ICD-10 — see
// Icd10SearchRepository for where that order becomes ranking weight
// (§5.2: department common list scores 500 - sort_rank, hospital-wide
// scores 300 - sort_rank).
public record CommonCode(
        UUID id,
        String code,
        UUID departmentId,
        int sortRank,
        UUID addedBy,
        Instant addedAt,
        boolean isActive
) {
}
