package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CashierShift;
import com.adags.modules.billing.domain.CashierShiftRepository;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 02-billing-architecture.md §9.1: "At shift end the cashier enters
// counted cash; the system computes expected, records variance."
// The cashier declares — reconciliation is a separate, later step by a
// different actor (ReconcileCashierShiftService).
@Service
public class CloseCashierShiftService {

    private final CashierShiftRepository cashierShiftRepository;

    public CloseCashierShiftService(CashierShiftRepository cashierShiftRepository) {
        this.cashierShiftRepository = cashierShiftRepository;
    }

    @Transactional
    public CashierShift close(UUID shiftId, UUID actorUserId, Money countedCash) {
        return cashierShiftRepository.close(shiftId, actorUserId, countedCash);
    }
}
