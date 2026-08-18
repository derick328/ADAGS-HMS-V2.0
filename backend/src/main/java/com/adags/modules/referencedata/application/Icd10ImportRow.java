package com.adags.modules.referencedata.application;

// One row of an external ICD-10 release. icd10_release itself is not part
// of the row — Icd10ImportService.importRelease takes it once for the
// whole batch, since one import call always imports one release.
public record Icd10ImportRow(
        String code,
        String title,
        String chapter,
        String block,
        String parentCode,
        boolean isBillable
) {
}
