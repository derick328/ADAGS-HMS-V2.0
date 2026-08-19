package com.adags.modules.insurance.application;

import com.adags.modules.insurance.domain.ClaimState;

import java.util.UUID;

// rejectedReason is only consulted (and required) when targetState is REJECTED.
public record TransitionInsuranceClaimCommand(UUID claimId, ClaimState targetState, String rejectedReason) {
}
