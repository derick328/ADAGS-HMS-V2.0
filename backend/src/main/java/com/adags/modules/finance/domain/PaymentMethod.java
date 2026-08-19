package com.adags.modules.finance.domain;

// Deliberately a separate enum from billing.domain.PaymentMethod — root
// CLAUDE.md "Modules": no cross-module entity imports. Same vocabulary
// (an expense is paid out the same rails a patient payment comes in on).
public enum PaymentMethod {
    CASH,
    DEBIT_CARD,
    CREDIT_CARD,
    MOBILE_MONEY,
    BANK_TRANSFER,
    CHEQUE
}
