package com.adags.modules.referencedata.domain;

import java.time.Instant;

// 10-clinical-reference-data.md §2.1. code is the primary key ('B50.9'),
// not a UUID — it IS the human-facing, universally-recognised identifier;
// manufacturing a surrogate key for it would just be an extra join.
//
// Never deleted (root CLAUDE.md "Data") — retirement sets isActive false,
// which pulls the code out of search (Icd10SearchRepository filters on it)
// while leaving it resolvable for any historical diagnosis that references
// it. isSelectable controls diagnosis entry separately from insurer billing
// eligibility: a valid terminal three-character category may be selectable.
public record Icd10Code(
        String code,
        String title,
        String chapter,
        String block,
        String parentCode,
        String icd10Release,
        boolean isBillable,
        boolean isActive,
        Instant createdAt,
        boolean isSelectable,
        String entryType
) {
    public Icd10Code(String code, String title, String chapter, String block, String parentCode,
                     String icd10Release, boolean isBillable, boolean isActive, Instant createdAt) {
        this(code, title, chapter, block, parentCode, icd10Release, isBillable, isActive, createdAt, true, null);
    }
}
