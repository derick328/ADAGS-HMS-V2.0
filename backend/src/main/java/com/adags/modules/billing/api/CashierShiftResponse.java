package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.CashierShift;
import com.adags.modules.billing.domain.CashierShiftStatus;

import java.time.Instant;
import java.util.UUID;

public record CashierShiftResponse(
        UUID id,
        UUID facilityId,
        UUID openedBy,
        Instant openedAt,
        CashierShiftStatus status,
        Instant closedAt,
        Long countedCashMinor,
        Long expectedCashMinor,
        Long varianceMinor,
        String currencyCode
) {

    public static CashierShiftResponse from(CashierShift shift) {
        return new CashierShiftResponse(shift.id(), shift.facilityId(), shift.openedBy(), shift.openedAt(),
                shift.status(), shift.closedAt(),
                shift.countedCash() == null ? null : shift.countedCash().amountMinor(),
                shift.expectedCash() == null ? null : shift.expectedCash().amountMinor(),
                shift.varianceCash() == null ? null : shift.varianceCash().amountMinor(),
                "TZS");
    }
}
