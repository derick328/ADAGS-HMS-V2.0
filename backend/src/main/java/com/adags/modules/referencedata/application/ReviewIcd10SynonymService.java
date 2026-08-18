package com.adags.modules.referencedata.application;

import com.adags.modules.referencedata.domain.Icd10Synonym;
import com.adags.modules.referencedata.domain.Icd10SynonymRepository;
import com.adags.modules.referencedata.domain.SelfReviewNotAllowedException;
import com.adags.modules.referencedata.domain.SynonymStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// 10-clinical-reference-data.md §3: "Clinical lead -> approves or rejects,
// with a note" — REFERENCE_SYNONYM_APPROVE-gated at the controller for
// both outcomes, same as CloseEncounterService handling multiple outcomes
// through one service. proposedBy != reviewedBy is checked here for a
// clean error before the DB CHECK (V13 migration) would reject it anyway.
@Service
public class ReviewIcd10SynonymService {

    private final Icd10SynonymRepository repository;
    private final Clock clock;

    public ReviewIcd10SynonymService(Icd10SynonymRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Icd10Synonym review(UUID synonymId, SynonymStatus decision, UUID reviewedBy, String reviewNote) {
        if (decision != SynonymStatus.APPROVED && decision != SynonymStatus.REJECTED) {
            throw new IllegalArgumentException("A review decision must be APPROVED or REJECTED, not " + decision + ".");
        }
        Icd10Synonym synonym = repository.findById(synonymId)
                .orElseThrow(() -> new NoSuchElementException("Synonym not found: " + synonymId));
        if (synonym.status() != SynonymStatus.PROPOSED) {
            throw new SynonymAlreadyReviewedException(synonymId);
        }
        if (synonym.proposedBy().equals(reviewedBy)) {
            throw new SelfReviewNotAllowedException();
        }

        Instant reviewedAt = Instant.now(clock);
        repository.review(synonymId, decision, reviewedBy, reviewedAt, reviewNote);

        return new Icd10Synonym(synonym.id(), synonym.code(), synonym.term(), synonym.language(), decision,
                synonym.proposedBy(), synonym.proposedAt(), reviewedBy, reviewedAt, reviewNote, synonym.isActive());
    }
}
