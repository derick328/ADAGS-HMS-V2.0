package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.PayerCategory;

import java.util.UUID;

// 02-billing-architecture.md §4: "Every charge is posted by a domain
// event. There is no manual charge-entry path." The caller is that event
// handler (e.g. TriageCompleted, Phase 3 item 5) — it already knows its
// own encounter's patientId/facilityId/payerCategory, so ChargePostingService
// never needs to reach back across the module boundary to resolve them.
//
// encounterId is nullable per Invoice's own model (a pharmacy walk-in or
// records fee has no encounter) even though every caller wired so far
// (triage) always supplies one.
public record ChargePostingCommand(
        UUID encounterId,
        UUID patientId,
        UUID facilityId,
        PayerCategory payerCategory,
        UUID insurancePolicyId,
        String serviceItemCode,
        int quantity,
        String sourceEventType,
        UUID sourceEventId,
        UUID actorUserId
) {
}
