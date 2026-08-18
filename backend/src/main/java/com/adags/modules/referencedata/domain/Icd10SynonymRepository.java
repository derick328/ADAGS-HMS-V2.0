package com.adags.modules.referencedata.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Icd10SynonymRepository {

    Icd10Synonym insert(Icd10Synonym synonym);

    Optional<Icd10Synonym> findById(UUID id);

    /** The clinical lead's review queue (10-clinical-reference-data.md §3). */
    List<Icd10Synonym> findByStatus(SynonymStatus status);

    void review(UUID id, SynonymStatus decision, UUID reviewedBy, Instant reviewedAt, String reviewNote);
}
