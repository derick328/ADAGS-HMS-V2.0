package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CashierShift;
import com.adags.modules.billing.domain.CashierShiftRepository;
import com.adags.modules.billing.domain.SelfReconciliationNotAllowedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

// 02-billing-architecture.md §9.1: the accountant reconciles and signs
// off — the cashier declares, they do not reconcile their own drawer.
// The friendly pre-flight check for the DB's second-person CHECK
// (reconciled_by != closed_by).
@Service
public class ReconcileCashierShiftService {

    private final CashierShiftRepository cashierShiftRepository;

    public ReconcileCashierShiftService(CashierShiftRepository cashierShiftRepository) {
        this.cashierShiftRepository = cashierShiftRepository;
    }

    @Transactional
    public CashierShift reconcile(UUID shiftId, UUID actorUserId) {
        CashierShift shift = cashierShiftRepository.findById(shiftId)
                .orElseThrow(() -> new NoSuchElementException("No cashier shift " + shiftId));
        if (actorUserId.equals(shift.closedBy())) {
            throw new SelfReconciliationNotAllowedException();
        }
        return cashierShiftRepository.reconcile(shiftId, actorUserId);
    }
}
