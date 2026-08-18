package com.adags.modules.clinical.domain;

import java.time.Instant;
import java.util.UUID;

// 07-doctor.md §4.1. icd10Code is a plain string, not a reference to
// referencedata's Icd10Code — clinical and referencedata are different
// modules (backend/CLAUDE.md "Module boundary rules" #3: cross-module FKs
// only to patient and encounter). icd10TitleSnapshot is copied at
// diagnosis time specifically so this record never needs to re-resolve
// through the live reference table: "ICD-10 titles are revised between
// releases, and a record written in 2026 must still read as it did in 2026."
public record Diagnosis(
        UUID id,
        UUID encounterId,
        UUID consultationRecordId,
        String icd10Code,
        String icd10TitleSnapshot,
        String freeTextClarifier,
        DiagnosisCertainty certainty,
        boolean isPrimary,
        UUID diagnosedBy,
        Instant diagnosedAt,
        UUID amendsId,
        String amendmentReason
) {
}
