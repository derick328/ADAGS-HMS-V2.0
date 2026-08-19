package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    /** Always inserts a DRAFT — no invoice number, freely editable. */
    Invoice insertDraft(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    List<Invoice> findByEncounterId(UUID encounterId);

    /**
     * DRAFT -> ISSUED: assigns invoiceNumber (from the gapless sequence,
     * allocated by the caller) and recomputes gross/insurance/patient
     * totals from the invoice's current lines, in the same statement that
     * locks the invoice at expectedVersion. 02-billing-architecture.md
     * §10: the number is allocated at issue, never at draft.
     *
     * @throws StaleInvoiceException if the invoice is no longer DRAFT at expectedVersion.
     */
    Invoice issue(UUID invoiceId, String invoiceNumber, int expectedVersion);

    /**
     * 02-billing-architecture.md §9.3: decrements outstandingTotal by
     * settledAmount and, if it reaches zero, transitions ISSUED -&gt;
     * PATIENT_PORTION_SETTLED in the same statement. Idempotent against
     * being called after the transition already happened — the state
     * only ever moves forward.
     */
    Invoice recordSettlement(UUID invoiceId, Money settledAmount);

    /**
     * ISSUED or CANCELLATION_PENDING -&gt; VOID, only when nothing has been
     * paid against it (outstandingTotal == grossTotal). §10: "Once any
     * payment has landed, void is blocked" — ApproveInvoiceCancellationService
     * checks that before ever calling this, routing a paid invoice to the
     * refund flow instead; this method's own WHERE clause is the
     * database-level backstop for that same rule.
     *
     * @throws InvoiceCannotBeVoidedException if the invoice is in neither state or has a settled amount.
     */
    Invoice voidUnpaid(UUID invoiceId, String reason, UUID voidedBy);

    /**
     * 18-admin.md §6: ISSUED or PATIENT_PORTION_SETTLED -&gt; CANCELLATION_PENDING
     * — "frozen, no payments accepted" is already enforced elsewhere
     * (PostPaymentService rejects CANCELLATION_PENDING invoices).
     * PATIENT_PORTION_SETTLED is eligible too: §10's "once payment has
     * landed, void is blocked, routed to the refund flow" only makes
     * sense if a cancellation can be requested against an already-paid
     * invoice in the first place.
     *
     * @throws InvoiceCancellationNotEligibleException if the invoice is in neither state.
     */
    Invoice markCancellationPending(UUID invoiceId);

    /**
     * 18-admin.md §6: CANCELLATION_PENDING -&gt; ISSUED or PATIENT_PORTION_SETTLED
     * — a rejected cancellation request returns the invoice to whichever
     * live state its outstandingTotal implies (0 -&gt; PATIENT_PORTION_SETTLED,
     * matching recordSettlement's own invariant that outstanding reaching
     * zero always means that state; otherwise ISSUED), never a fixed
     * ISSUED regardless of payment history. "The invoice is always in a
     * definite state — alive and collectable, or dead. It is never left
     * frozen after a decision."
     *
     * @throws InvoiceCancellationNotEligibleException if the invoice is not CANCELLATION_PENDING.
     */
    Invoice returnToIssuedFromCancellationPending(UUID invoiceId);

    /**
     * 02-billing-architecture.md §8: "if balance &gt; 0: create receivable."
     * ISSUED -&gt; RECEIVABLE_POST_DISCHARGE, only when outstandingTotal &gt; 0
     * — a no-op (state unchanged) otherwise. Deliberately cannot throw:
     * root CLAUDE.md "Billing" — there must be no code path where this
     * call blocks the discharge that triggers it. Also stamps
     * receivableAt — the aging clock for the aged-receivables report,
     * set once and never touched again.
     */
    Invoice markReceivablePostDischarge(UUID invoiceId);

    /**
     * 02-billing-architecture.md §9.3: decrements outstandingTotal by
     * amount, the write-off component of that formula — same mechanism as
     * recordSettlement, just a different reason the balance moved. Only
     * from RECEIVABLE_POST_DISCHARGE, and only up to the current
     * outstanding balance: the WHERE clause (not a prior read) is the
     * concurrency guard (root CLAUDE.md "Concurrency" — a database
     * constraint, not an application check), so two write-offs racing
     * against the same invoice can't together exceed it.
     *
     * @throws InvoiceWriteOffExceedsOutstandingException if the invoice is not RECEIVABLE_POST_DISCHARGE
     *         or amount exceeds the outstanding balance at the moment of the update.
     */
    Invoice recordWriteOff(UUID invoiceId, Money amount);

    /**
     * 02-billing-architecture.md §9.3: decrements outstandingTotal by
     * amount, the credit_notes component of that formula. Eligible
     * against any invoice not in DRAFT, VOID, or CANCELLATION_PENDING —
     * broader than recordWriteOff, since a wrong price or an undelivered
     * service may be discovered before or after discharge. Same
     * WHERE-clause concurrency guard as recordWriteOff.
     *
     * @throws CreditNoteExceedsOutstandingException if the invoice is not eligible or amount
     *         exceeds the outstanding balance at the moment of the update.
     */
    Invoice recordCreditNote(UUID invoiceId, Money amount);

    /**
     * 02-billing-architecture.md §9.3: increments outstandingTotal by
     * amount, the "+ refunds" component — money already counted as
     * settled is now owed again. Deliberately does not touch the
     * consumed line's ServiceAuthorization or invoice state: per root
     * CLAUDE.md "Billing", an authorization already granted is durable
     * and survives a later refund the same way it survives a bypass
     * revocation. No state restriction — a refund is eligible whenever
     * the payment_allocation it reverses was ever settled, regardless of
     * what the invoice has done since.
     */
    Invoice recordRefund(UUID invoiceId, Money amount);
}
