package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.shared.kernel.Money;

import java.util.UUID;

// 04-procedure-bedday-rules.md §3.5: the bed-day accrual caller already
// knows the exact amount (Ward.daily_rate_minor, snapshotted) — this is
// ChargePostingCommand's sibling for a caller that resolved its own price
// instead of asking PriceResolutionService to.
public record FixedAmountChargePostingCommand(
        UUID encounterId,
        UUID patientId,
        UUID facilityId,
        PayerCategory payerCategory,
        String serviceItemCode,
        Money amount,
        String sourceEventType,
        UUID sourceEventId,
        UUID actorUserId
) {
}
