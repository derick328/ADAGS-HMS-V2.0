package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 02-billing-architecture.md §9.1. counted/expected/variance are null
// until CLOSED — expected is computed by the system (settled CASH
// payments in this shift), counted is what the cashier declares,
// variance is the difference. reconciledBy/reconciledAt are null until
// RECONCILED — a distinct actor from closedBy, enforced by a DB CHECK.
public record CashierShift(
        UUID id,
        UUID facilityId,
        UUID openedBy,
        Instant openedAt,
        CashierShiftStatus status,
        UUID closedBy,
        Instant closedAt,
        Money countedCash,
        Money expectedCash,
        Money varianceCash,
        UUID reconciledBy,
        Instant reconciledAt
) {

    public static CashierShift open(UUID facilityId, UUID openedBy) {
        return new CashierShift(null, facilityId, openedBy, null, CashierShiftStatus.OPEN,
                null, null, null, null, null, null, null);
    }
}
