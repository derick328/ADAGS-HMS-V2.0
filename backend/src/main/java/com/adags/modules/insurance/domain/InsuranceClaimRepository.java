package com.adags.modules.insurance.domain;

import java.util.Optional;
import java.util.UUID;

public interface InsuranceClaimRepository {

    /** Inserts a DRAFT row — claim.claimNo() already allocated (InsuranceClaim.raise). */
    InsuranceClaim insert(InsuranceClaim claim);

    Optional<InsuranceClaim> findById(UUID id);

    /**
     * Moves claimId from expectedCurrentState to newState — the WHERE
     * clause (not a prior read) is the concurrency guard (root CLAUDE.md
     * "Concurrency"), so two decisions racing against the same claim
     * can't both apply.
     *
     * @throws InsuranceClaimStateConflictException if the claim is not currently in expectedCurrentState.
     */
    InsuranceClaim transition(UUID claimId, ClaimState expectedCurrentState, ClaimState newState, String rejectedReason);
}
