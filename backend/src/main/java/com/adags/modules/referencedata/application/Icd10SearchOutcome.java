package com.adags.modules.referencedata.application;

import com.adags.modules.referencedata.domain.Icd10SearchResult;

import java.util.List;
import java.util.UUID;

// logId is handed back to the caller so a later pick can be reported via
// Icd10SearchService.recordSelection — 10-clinical-reference-data.md §5.4's
// ReferenceSearchLog row starts with selectedId null and is filled in once
// the clinician actually picks a result, which is a separate round trip.
public record Icd10SearchOutcome(UUID logId, List<Icd10SearchResult> results) {
}
