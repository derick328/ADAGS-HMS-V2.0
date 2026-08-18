package com.adags.modules.referencedata.api;

import com.adags.modules.referencedata.domain.Icd10Synonym;
import com.adags.modules.referencedata.domain.SynonymStatus;

import java.time.Instant;
import java.util.UUID;

public record SynonymResponse(
        UUID id,
        String code,
        String term,
        String language,
        SynonymStatus status,
        UUID proposedBy,
        Instant proposedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote
) {
    public static SynonymResponse from(Icd10Synonym synonym) {
        return new SynonymResponse(synonym.id(), synonym.code(), synonym.term(), synonym.language(),
                synonym.status(), synonym.proposedBy(), synonym.proposedAt(),
                synonym.reviewedBy(), synonym.reviewedAt(), synonym.reviewNote());
    }
}
