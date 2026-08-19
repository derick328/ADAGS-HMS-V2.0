package com.adags.modules.insurance.domain;

import java.util.UUID;

// root CLAUDE.md "Concurrency": the WHERE-guarded UPDATE behind
// InsuranceClaimRepository.transition is the real guard against two
// decisions racing on the same claim; this is what the loser gets.
public class InsuranceClaimStateConflictException extends RuntimeException {

    private final UUID claimId;

    public InsuranceClaimStateConflictException(UUID claimId, ClaimState expectedCurrentState) {
        super("Insurance claim " + claimId + " is not currently " + expectedCurrentState + " — someone else already decided it.");
        this.claimId = claimId;
    }

    public UUID claimId() {
        return claimId;
    }
}
