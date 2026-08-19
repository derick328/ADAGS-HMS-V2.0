package com.adags.modules.insurance.domain;

// Phase 7 item 3 part 3: "DRAFT -> SUBMITTED -> APPROVED | REJECTED;
// REJECTED -> RESUBMITTED -> APPROVED | REJECTED." Anything not on that
// graph (ClaimState.canTransitionTo) is rejected before it ever reaches
// the database.
public class InvalidClaimTransitionException extends RuntimeException {

    public InvalidClaimTransitionException(ClaimState from, ClaimState to) {
        super("Cannot transition an insurance claim from " + from + " to " + to + ".");
    }
}
