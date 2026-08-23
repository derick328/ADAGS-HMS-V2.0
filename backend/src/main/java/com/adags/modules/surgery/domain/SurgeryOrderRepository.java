package com.adags.modules.surgery.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgeryOrderRepository {

    /** @throws TheatreSlotNotAvailableException if the (theatre, date, time_slot) slot is already booked. */
    SurgeryOrder insert(SurgeryOrder surgeryOrder);

    Optional<SurgeryOrder> findById(UUID id);

    List<SurgeryOrder> findByEncounterId(UUID encounterId);

    /**
     * WHERE-guarded on {@code status = :from} — "the repository's
     * WHERE-guarded UPDATE is the real guard" (same idiom as
     * InvoiceWriteOffRepository et al.). Returns false if the order was
     * not in {@code from} at the time of the update (already transitioned
     * concurrently, or the caller's own precondition was stale).
     */
    boolean transitionStatus(UUID id, SurgeryOrderStatus from, SurgeryOrderStatus to);

    /**
     * Task text (item 1): "Check again at procedure start — consent may be
     * revoked." WHERE-guarded on {@code consent_obtained = TRUE} so a
     * revoke can't race a start (StartSurgeryOrderService reads consent
     * from the same row it's about to transition). Returns false if
     * consent was already false.
     */
    boolean revokeConsent(UUID id);

    /**
     * Phase 8 item 5. WHERE-guarded on {@code status = 'COMPLETED' AND
     * disposition IS NULL} — set exactly once, only after COMPLETED.
     * Returns false if the order is not COMPLETED or already has one.
     */
    boolean recordDisposition(UUID id, SurgeryDisposition disposition, UUID recordedBy);

    /**
     * Phase 8 item 6. Sets status = CANCELLED and the cancellation columns
     * in one WHERE-guarded UPDATE — WHERE-guarded on {@code status IN
     * ('SCHEDULED', 'IN_PROGRESS')}, the same set SurgeryOrderStatus's own
     * transition graph allows into CANCELLED. Returns false if the order
     * was already terminal (COMPLETED or CANCELLED).
     */
    boolean recordCancellation(UUID id, CancellationReason reason, UUID cancelledBy);

    /**
     * Phase 8 item 8: resolves a DRAFT_EMERGENCY order's free-text
     * procedureName to a real catalogue code and posts the procedure fee
     * (caller does that via BillingFacade first, passing the resulting
     * invoiceLineId here) — sets all three columns and transitions
     * DRAFT_EMERGENCY -> IN_PROGRESS in one WHERE-guarded UPDATE. Returns
     * false if the order was not DRAFT_EMERGENCY.
     */
    boolean reconcileEmergencyDraft(UUID id, String procedureServiceItemCode, String procedureName, UUID invoiceLineId);

    /** Phase 8 item 8: DRAFT_EMERGENCY orders older than olderThan, never yet escalated — the governance job's candidate set. */
    List<SurgeryOrder> findStaleDrafts(Instant olderThan);

    /** Phase 8 item 8: WHERE-guarded on {@code governance_escalated_at IS NULL} — fires the alert exactly once per draft. */
    boolean markGovernanceEscalated(UUID id);
}
