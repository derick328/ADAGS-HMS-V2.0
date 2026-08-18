package com.adags.modules.billing.domain;

// 03-billing-addendum.md §1: "allocated_to_line == line.patient_portion
// exactly, anything less is REJECTED" unless an active PaymentBypass
// exists. No automatic allocation, no partial outside that one exception.
public class PaymentAllocationRejectedException extends RuntimeException {

    public PaymentAllocationRejectedException(String message) {
        super(message);
    }
}
