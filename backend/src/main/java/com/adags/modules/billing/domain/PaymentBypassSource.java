package com.adags.modules.billing.domain;

// 02-billing-architecture.md §5.2: IMMEDIATE grants automatically.
//
// SURGERY_IMMEDIATE: Phase 8 item 1, root CLAUDE.md "Safety": "IMMEDIATE
// triage priority bypasses the payment gate for the whole encounter" — the
// surgery module's own IMMEDIATE urgency creates one the same way, scoped
// to the same encounter, so the existing gate's encounter-scoped lookup
// (BillingGateImpl.evaluate) needs no change to cover it.
//
// CLINICIAN_AUTHORISED: §5.2/§8's "URGENT nurse may declare with a
// reason... LESS_URGENT/below clinician authorisation required" —
// EMERGENCY_DECLARE_AUTHORISE (V5, held by CLINICAL_LEAD) already gates
// the permission side of this, but there is still no endpoint that grants
// it: that needs a clinician to see and act on a request, which needs a
// Doctor workspace that doesn't exist yet in this frontend. Present in
// the enum (and V61's widened CHECK) for completeness; not yet
// constructible via BillingFacade.
//
// ADMIN_WAIVER: §8's "charity, staff benefit, ministry directive" — an
// admin grants this directly, no second actor, via
// BillingFacade.createAdminWaiverPaymentBypass.
public enum PaymentBypassSource {
    TRIAGE_IMMEDIATE,
    SURGERY_IMMEDIATE,
    CLINICIAN_AUTHORISED,
    ADMIN_WAIVER
}
