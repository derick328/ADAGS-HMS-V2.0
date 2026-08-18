package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.util.Optional;
import java.util.UUID;

public interface CashierShiftRepository {

    CashierShift open(CashierShift shift);

    Optional<CashierShift> findById(UUID id);

    Optional<CashierShift> findOpenByOpenedBy(UUID openedBy);

    /**
     * OPEN -&gt; CLOSED. Computes expected from settled CASH payments
     * recorded against this shift and records the variance against
     * countedCash — the cashier's declaration, not a reconciliation.
     *
     * @throws CashierShiftNotOpenException if the shift is not OPEN.
     */
    CashierShift close(UUID shiftId, UUID closedBy, Money countedCash);

    /**
     * CLOSED -&gt; RECONCILED — the accountant's sign-off. The database
     * itself rejects reconciledBy == closedBy (root CLAUDE.md
     * "Authorisation": second-person constraints).
     *
     * @throws CashierShiftNotClosedException if the shift is not CLOSED.
     */
    CashierShift reconcile(UUID shiftId, UUID reconciledBy);
}
