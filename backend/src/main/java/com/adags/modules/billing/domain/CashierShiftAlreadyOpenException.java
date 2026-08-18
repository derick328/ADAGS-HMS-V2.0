package com.adags.modules.billing.domain;

// One open shift per cashier at a time — the friendly pre-flight version
// of the partial unique index on cashier_shift(opened_by) WHERE status = 'OPEN'.
public class CashierShiftAlreadyOpenException extends RuntimeException {

    public CashierShiftAlreadyOpenException(String message) {
        super(message);
    }
}
