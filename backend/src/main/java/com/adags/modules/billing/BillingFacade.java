package com.adags.modules.billing;

import com.adags.shared.kernel.Money;
import com.adags.shared.kernel.PayerType;

import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"; backend/CLAUDE.md "Naming" names BillingFacade explicitly).
// Every parameter type here is a UUID, a String, or a shared.kernel type
// — never a billing.domain type — so a caller (encounters) never needs to
// import across the module boundary to use this. Exceptions thrown are
// unchecked billing.domain types (BillingGateRejectedException,
// InvoiceCannotBeVoidedException, ...); a caller that does not catch them
// — the normal case, letting them propagate to GlobalExceptionHandler —
// never needs to import them either.
public interface BillingFacade {

    /**
     * 02-billing-architecture.md §5.1: grants an active, encounter-scoped
     * bypass. Persists a PaymentBypass row. Returns its id.
     */
    UUID createImmediateTriagePaymentBypass(UUID encounterId, String reason, UUID grantedBy);

    /**
     * Phase 8 item 1, root CLAUDE.md "Safety": IMMEDIATE surgery urgency
     * bypasses the payment gate for the whole encounter, the same
     * mechanism and scope as {@link #createImmediateTriagePaymentBypass}
     * with source PaymentBypassSource.SURGERY_IMMEDIATE instead of
     * TRIAGE_IMMEDIATE. Persists a PaymentBypass row. Returns its id.
     */
    UUID createImmediateSurgeryPaymentBypass(UUID encounterId, String reason, UUID grantedBy);

    /**
     * 02-billing-architecture.md §8: "charity, staff benefit, ministry
     * directive" — an admin grants this directly, no second actor
     * (unlike CLINICIAN_AUTHORISED, which needs a clinician to complete
     * a receptionist-initiated request — not built yet, no Doctor
     * workspace exists to receive one). Same mechanics as the two
     * IMMEDIATE bypass grants: an active, encounter-scoped bypass row.
     * Persists a PaymentBypass row. Returns its id.
     */
    UUID createAdminWaiverPaymentBypass(UUID encounterId, String reason, UUID grantedBy);

    /**
     * 02-billing-architecture.md §4.1/§4.2: posts the consultation charge.
     * The service item resolves from the assigned provider's actual role
     * — DOCTOR -&gt; CONSULT_GENERAL, SPECIALIST_DOCTOR -&gt; CONSULT_SPECIALIST
     * — never a client-supplied field. patientId/facilityId/payerType come
     * from the caller's own Encounter, since this module cannot read that
     * table itself (root CLAUDE.md "Modules": no cross-module entity
     * imports). A missing price, or a provider holding neither clinical
     * role, blocks with an explicit error — the whole caller transaction
     * is meant to roll back with it (§4.1: "a triaged patient with no
     * charge walks straight past the cashier").
     */
    void postConsultationCharge(UUID encounterId, UUID patientId, UUID facilityId, PayerType payerType,
                                 UUID triageAssessmentId, UUID assignedProviderId, UUID actorUserId);

    /**
     * Preview-only: the consultation fee a given provider would be charged
     * at, for 06-triage.md §5.1's referral picker ("the fee is visible
     * BEFORE selection — the nurse is choosing between 10,000/= and
     * 40,000/= and must be told"). Resolves the same
     * role -&gt; service-item -&gt; price path postConsultationCharge uses,
     * but posts nothing. Throws (unchecked) if the provider holds no role
     * mapped to a consultation price, or no current price is configured —
     * the same failures postConsultationCharge itself can throw, just
     * surfaced before commit rather than during it.
     */
    Money previewConsultationFee(UUID providerId, PayerType payerType);

    /**
     * 02-billing-architecture.md §2.1/§3: the one deliberate payment gate
     * — consultation entry. Checks the encounter's consultation invoice
     * line against BillingGate and, on success, durably authorizes it.
     * Throws (unchecked) if unsatisfied, carrying the outstanding amount
     * and service name for a 402 (already wired in GlobalExceptionHandler).
     */
    void authorizeConsultationEntry(UUID encounterId, UUID actorUserId);

    /**
     * 02-billing-architecture.md §8.1: a patient in AWAITING_PAYMENT who
     * cannot pay leaves. Voids the encounter's unpaid consultation charge
     * with a reason — never deleted, never left outstanding forever.
     * Only succeeds while nothing has been paid against it.
     */
    void voidUnpaidConsultationCharge(UUID encounterId, String reason, UUID actorUserId);

    /**
     * 02-billing-architecture.md §4: posts a single charge line for a
     * catalogue service item, as a freshly issued single-line invoice —
     * the generic version of postConsultationCharge for every other
     * event-driven charge (lab order, prescription, procedure, ...).
     * sourceEventType/sourceEventId identify which domain event posted it,
     * same convention as ("TriageCompleted", triageAssessmentId). Returns
     * the created invoice line id so the caller can gate consumption
     * against it later via authorizeConsumption. Throws
     * PriceNotConfiguredException (unchecked) if no active service item or
     * no current price exists for serviceItemCode/payerType — the whole
     * caller transaction is meant to roll back with it.
     */
    UUID postServiceCharge(UUID encounterId, UUID patientId, UUID facilityId, PayerType payerType,
                            String serviceItemCode, int quantity, String sourceEventType, UUID sourceEventId,
                            UUID actorUserId);

    /**
     * 02-billing-architecture.md §2.1/§3: the one deliberate payment gate,
     * generic across every gate position (root CLAUDE.md "Billing":
     * consultation entry, lab specimen collection, pharmacy dispensing,
     * procedure start) — the generic version of authorizeConsultationEntry
     * for callers that already know their own invoiceLineId (from
     * postServiceCharge). Checks invoiceLineId against BillingGate and, on
     * success, durably authorizes it. Throws BillingGateRejectedException
     * (unchecked) if unsatisfied, carrying the outstanding amount and
     * service name for a 402 (already wired in GlobalExceptionHandler).
     * A line already carrying an authorization is a no-op — durable per
     * §3.1 clause 4, so a second call (e.g. specimen recollection after a
     * quality rejection) never re-charges or re-blocks.
     */
    void authorizeConsumption(UUID invoiceLineId, UUID actorUserId);

    /**
     * The generic version of voidUnpaidConsultationCharge, for any line
     * postServiceCharge created — 12-pharmacist.md §5.2/§6.2: an
     * UNAVAILABLE or substituted-away prescription line's unpaid charge is
     * voided automatically. Returns true if voided; returns false,
     * without throwing, if something has already been settled against it
     * — the caller is expected to route that case to an accountant-led
     * refund instead, which this codebase does not yet automate
     * (PAYMENT_REFUND exists as a permission with no service behind it).
     */
    boolean voidUnpaidServiceCharge(UUID invoiceLineId, String reason, UUID actorUserId);

    /**
     * 12-pharmacist.md §9.2: what an approved PharmacyItemProposal (a new
     * item's first price, or a price change) actually does — creates the
     * service_item if this is the first price ever set for the code, and
     * versions the CASH price_list_entry (root CLAUDE.md "Billing":
     * "Close valid_to, open a new row," never an edit in place). Returns
     * the new entry's id.
     */
    UUID setPharmacyItemPrice(String itemCode, String itemName, long priceAmountMinor, UUID actorUserId);

    /** The current CASH price for a service item code, if one has ever been set — 12-pharmacist.md §9.2's current_price_minor snapshot at proposal time. */
    Optional<Money> findCurrentPharmacyItemPrice(String itemCode);

    /**
     * Phase 8 item 1: resolves and returns the catalogue name for an
     * active PROCEDURE-category service item code, for the caller to
     * snapshot at scheduling time — same pattern and same reasoning as
     * ReferenceDataFacade.resolveBillableIcd10Title (AddDiagnosisService):
     * resolved server-side from the live catalogue, never trusted from the
     * client, then copied so a later catalogue rename doesn't rewrite
     * history. Empty if no such active, PROCEDURE-category service item
     * exists.
     */
    Optional<String> findActiveProcedureName(String serviceItemCode);

    /**
     * 04-procedure-bedday-rules.md §3.5: posts a single retrospective
     * bed-day charge as a freshly issued, single-line invoice — same
     * mechanics as postServiceCharge, but amount is caller-supplied
     * (Ward.daily_rate_minor, snapshotted) rather than resolved from
     * price_list_entry; serviceItemCode still names a real, active
     * ServiceItem (WARD_BED_DAY_GENERAL / WARD_BED_DAY_PRIVATE) so the
     * line categorizes correctly in reports. Returns the invoice line id.
     */
    UUID postFixedAmountCharge(UUID encounterId, UUID patientId, UUID facilityId, PayerType payerType,
                                String serviceItemCode, Money amount, String sourceEventType, UUID sourceEventId,
                                UUID actorUserId);

    /**
     * 08-doctor-navigation.md §5.1: "PENDING_PAYMENT — charge posted, gate
     * not satisfied" is exactly {@code authorization_id IS NULL} on the
     * line (root CLAUDE.md "Billing": the gate is durably satisfied the
     * moment it's authorized, never re-checked). Request History's status
     * derivation is the first caller — a lab/prescription read model that
     * needs to know payment state without duplicating BillingGate logic.
     * Null-safe: an order/line with no invoice line yet (or none at all)
     * is unauthorized, not an error — callers pass invoiceLineId() straight
     * through without their own null guard.
     */
    boolean isLineAuthorized(UUID invoiceLineId);

    /**
     * 15-lab-technician.md §3.1: the lab queue's "Emergency" sub-tab —
     * an ACTIVE PaymentBypass exists for this encounter right now. Distinct
     * from {@link #isLineAuthorized}, which stays true forever once a line
     * clears the gate (root CLAUDE.md: "satisfies the gate permanently —
     * an order placed under a bypass still fulfils after that bypass is
     * revoked") and so cannot tell a queue whether the bypass is still
     * live. Backed by the same PaymentBypassRepository query
     * {@code /api/payment-bypasses/active} already reads.
     */
    boolean hasActivePaymentBypass(UUID encounterId);

    /**
     * 02-billing-architecture.md §8 / root CLAUDE.md "Billing": "if
     * balance &gt; 0: create receivable" — never a blocker. For every
     * ISSUED invoice on this encounter still carrying an outstanding
     * balance, transitions it to RECEIVABLE_POST_DISCHARGE. Cannot throw:
     * this is called from discharge completion, and there must be no
     * code path where an unpaid balance prevents that. Returns the total
     * amount moved to receivable (zero if nothing was outstanding).
     */
    Money createReceivablesForOutstandingBalance(UUID encounterId);
}
