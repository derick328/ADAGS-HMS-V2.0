package com.adags.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceWriteOffRepository {

    InvoiceWriteOff insert(InvoiceWriteOff writeOff);

    Optional<InvoiceWriteOff> findById(UUID id);

    /** Requests still awaiting a second signature — the review queue. */
    List<InvoiceWriteOff> findPending();

    /** Persists status/decidedBy/decidedAt/decisionNote. */
    InvoiceWriteOff decide(UUID id, WriteOffStatus status, UUID decidedBy, String decisionNote);
}
