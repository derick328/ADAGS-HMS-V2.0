package com.adags.modules.insurance.domain;

// 02-billing-architecture.md §11's InsuranceClaim data model. Only DRAFT
// is produced in this part — submission, approval and settlement are a
// later phase, modelled now for the same reason InvoiceState models its
// full graph before every transition has a caller.
public enum ClaimState {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    SETTLED
}
