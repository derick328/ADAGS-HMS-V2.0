package com.adags.modules.billing.domain;

import java.util.UUID;

// 02-billing-architecture.md §3: "One function, called server-side at the
// point of resource consumption." The interface lives here in domain —
// root CLAUDE.md "Billing": "The billing gate lives in the domain layer,
// called from a repository base class or an aspect. A controller-only
// check is defeated by a stale page, a direct call, or a race." Same
// split as every other repository in this module: the contract is
// domain, the I/O is infrastructure.BillingGateImpl.
//
// No consuming endpoint calls this yet — lab specimen collection,
// pharmacy dispensing and procedure start (§2's gated steps) all live in
// modules that don't exist yet. It is built real now, the same way
// PaymentBypass and ServiceItem were built ahead of their first caller,
// so the first real endpoint inherits the check rather than someone
// having to remember to add it.
public interface BillingGate {

    /**
     * True if this line is currently fulfillable. Read-only, no side
     * effects — safe for a status display. §3.1: evaluated live, never
     * cached, never stamped at order time.
     */
    boolean isSatisfied(UUID invoiceLineId);

    /**
     * Call this at the actual point of consumption (specimen collection,
     * dispensing, procedure start) — not for a preview check. Same
     * evaluation as isSatisfied, but on success it also durably records
     * *why*, when the reason is a revocable grant: if the line has no
     * authorization yet and is satisfied by an active bypass (today — a
     * future payment or insurance clause extends this the same way), a
     * ServiceAuthorization row is written and invoice_line.authorization_id
     * is set. Once set it is NEVER cleared (§3.1 clause 4) — an order
     * placed under a bypass still fulfils after the bypass is later
     * revoked.
     *
     * @throws BillingGateRejectedException if unsatisfied — carries the
     *         outstanding amount and service name for the caller's 402 (§3.3).
     */
    void authorizeConsumption(UUID invoiceLineId, UUID actorUserId);
}
