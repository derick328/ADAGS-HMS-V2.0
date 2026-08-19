package com.adags.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceCancellationRequestRepository {

    /**
     * @throws InvoiceAlreadyHasPendingCancellationException if invoiceId already has a PENDING request
     *         (the partial unique index is the real guard; this is the translated race-loser case too).
     */
    InvoiceCancellationRequest insert(InvoiceCancellationRequest request);

    Optional<InvoiceCancellationRequest> findById(UUID id);

    /** The admin's review queue — also what a site-wide pending-count badge counts. */
    List<InvoiceCancellationRequest> findPending();

    /** Persists status/reviewedBy/reviewedAt/reviewNote. */
    InvoiceCancellationRequest decide(UUID id, CancellationRequestStatus status, UUID reviewedBy, String reviewNote);
}
