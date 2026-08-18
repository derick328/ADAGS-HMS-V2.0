package com.adags.modules.billing.domain;

// 02-billing-architecture.md §9.2: INSURANCE deliberately not a method —
// coverage is a receivable, not cash received; marking an invoice paid
// because NHIF is covering it makes the cash position fiction until a
// remittance reconciles months later.
public enum PaymentMethod {
    CASH,
    DEBIT_CARD,
    CREDIT_CARD,
    MOBILE_MONEY,
    BANK_TRANSFER,
    CHEQUE
}
