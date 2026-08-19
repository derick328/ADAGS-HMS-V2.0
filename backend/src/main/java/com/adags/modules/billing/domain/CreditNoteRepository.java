package com.adags.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditNoteRepository {

    CreditNote insert(CreditNote creditNote);

    Optional<CreditNote> findById(UUID id);

    /** Credit notes still awaiting a second signature — the review queue. */
    List<CreditNote> findPending();

    /** Persists status/approvedBy/approvedAt/decisionNote. */
    CreditNote decide(UUID id, CreditNoteStatus status, UUID approvedBy, String decisionNote);
}
