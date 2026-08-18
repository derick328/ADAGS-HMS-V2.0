package com.adags.modules.referencedata.domain;

// 10-clinical-reference-data.md §5.2 — one ranked row. score is exposed
// (not just the ordering) so a caller/test can assert on why a result
// ranked where it did, not only that it did.
public record Icd10SearchResult(String code, String title, boolean isBillable, double score) {
}
