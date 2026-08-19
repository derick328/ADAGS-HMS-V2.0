package com.adags.modules.insurance.domain;

// 02-billing-architecture.md §11's InsuranceClaim data model. Phase 7
// item 3 part 3: "DRAFT -> SUBMITTED -> APPROVED | REJECTED; REJECTED ->
// RESUBMITTED -> APPROVED | REJECTED." Transitions only — no actual
// submission transport exists (no scheme API); canTransitionTo is what a
// TransitionInsuranceClaimService checks before writing.
//
// SETTLED is modelled ahead of its own caller (remittance reconciliation,
// not built in this part) — same reasoning as InvoiceState modelling its
// full graph before every transition has one — and deliberately has no
// legal incoming edge here yet.
public enum ClaimState {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    RESUBMITTED,
    SETTLED;

    public boolean canTransitionTo(ClaimState target) {
        return switch (this) {
            case DRAFT -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case REJECTED -> target == RESUBMITTED;
            case RESUBMITTED -> target == APPROVED || target == REJECTED;
            case APPROVED, SETTLED -> false;
        };
    }
}
