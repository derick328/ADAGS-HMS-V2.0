package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CashierShift;
import com.adags.modules.billing.domain.CashierShiftAlreadyOpenException;
import com.adags.modules.billing.domain.CashierShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 02-billing-architecture.md §9.1. The friendly pre-flight check for the
// partial unique index on cashier_shift(opened_by) WHERE status = 'OPEN'
// — same pattern as ReviewIcd10SynonymService's SelfReviewNotAllowedException.
@Service
public class OpenCashierShiftService {

    private final CashierShiftRepository cashierShiftRepository;

    public OpenCashierShiftService(CashierShiftRepository cashierShiftRepository) {
        this.cashierShiftRepository = cashierShiftRepository;
    }

    @Transactional
    public CashierShift open(UUID facilityId, UUID actorUserId) {
        if (cashierShiftRepository.findOpenByOpenedBy(actorUserId).isPresent()) {
            throw new CashierShiftAlreadyOpenException(
                    "User " + actorUserId + " already has an open cashier shift — close it before opening another.");
        }
        return cashierShiftRepository.open(CashierShift.open(facilityId, actorUserId));
    }
}
