package com.adags.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceLineRepository {

    /**
     * @throws InvoiceNotEditableException if the parent invoice is not DRAFT.
     */
    InvoiceLine insert(InvoiceLine line);

    List<InvoiceLine> findByInvoiceId(UUID invoiceId);

    Optional<InvoiceLine> findById(UUID id);

    /** The consultation line for this encounter — source_event_type = 'TriageCompleted', most recent first. */
    Optional<InvoiceLine> findConsultationLineByEncounterId(UUID encounterId);

    /**
     * Sets authorization_id — only if it is currently NULL. Never updates
     * or clears an existing one (02-billing-architecture.md §3.1 clause
     * 4: durable provenance). Returns false if the line already carried
     * an authorization (a no-op, not an error — the caller's job is
     * already done).
     */
    boolean setAuthorization(UUID invoiceLineId, UUID authorizationId);
}
