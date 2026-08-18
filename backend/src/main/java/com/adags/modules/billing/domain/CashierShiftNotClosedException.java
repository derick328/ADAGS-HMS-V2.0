package com.adags.modules.billing.domain;

// Reconciliation requires the cashier's own declaration first — a shift
// still OPEN, or already RECONCILED, has nothing for the accountant to sign off on.
public class CashierShiftNotClosedException extends RuntimeException {

    public CashierShiftNotClosedException(String message) {
        super(message);
    }
}
