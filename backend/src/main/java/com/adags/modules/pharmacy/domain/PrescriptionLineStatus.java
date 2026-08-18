package com.adags.modules.pharmacy.domain;

// 12-pharmacist.md §4.4, §5.1, §5.2. CANCELLED: superseded by an approved
// substitution. UNAVAILABLE: the pharmacist could not fulfil it and the
// doctor must re-prescribe or cancel. Both are terminal, like COMPLETED.
public enum PrescriptionLineStatus {
    AWAITING_DISPENSE, PARTIALLY_DISPENSED, COMPLETED, CANCELLED, UNAVAILABLE
}
