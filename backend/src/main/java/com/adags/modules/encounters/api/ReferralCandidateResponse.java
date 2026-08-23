package com.adags.modules.encounters.api;

import java.util.UUID;

// 06-triage.md §5.1's referral picker card. onDuty is deliberately null
// (unknown) rather than guessed — no shift-roster wiring exists for this
// yet (this pass scoped to real fee + real queue depth only; see
// ReferralCandidateController's class comment). feeAmountMinor/currencyCode
// are null together when no price resolves for this payer type — the UI
// must show "fee unavailable," never a fabricated number or a silent zero.
public record ReferralCandidateResponse(
        UUID providerId,
        String username,
        String roleLabel,
        Long feeAmountMinor,
        String currencyCode,
        int queueDepth,
        Boolean onDuty
) {
}
