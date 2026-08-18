package com.adags.modules.billing.domain;

// 02-billing-architecture.md §9.1: "PAYMENT_CREATE fails with a clear
// prompt when none is open" / "Shift close takes a lock; late payments
// are rejected with a prompt to open a new shift."
public class CashierShiftNotOpenException extends RuntimeException {

    public CashierShiftNotOpenException(String message) {
        super(message);
    }
}
