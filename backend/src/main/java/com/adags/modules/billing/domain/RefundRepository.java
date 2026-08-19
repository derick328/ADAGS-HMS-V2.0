package com.adags.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {

    Refund insert(Refund refund);

    Optional<Refund> findById(UUID id);

    /** Refunds still awaiting a second signature — the review queue. */
    List<Refund> findPending();

    /** Persists status/decidedBy/decidedAt/decisionNote. */
    Refund decide(UUID id, RefundStatus status, UUID decidedBy, String decisionNote);
}
