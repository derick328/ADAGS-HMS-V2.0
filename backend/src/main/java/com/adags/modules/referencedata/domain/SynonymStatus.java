package com.adags.modules.referencedata.domain;

// 10-clinical-reference-data.md §3: "propose freely, approve deliberately"
// — mirrors the invoice-cancellation pattern. Only APPROVED synonyms
// participate in search (Icd10SearchRepository).
public enum SynonymStatus {
    PROPOSED,
    APPROVED,
    REJECTED
}
