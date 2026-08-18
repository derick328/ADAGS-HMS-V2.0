package com.adags.modules.billing.domain;

// 02-billing-architecture.md §5.2: IMMEDIATE grants automatically; URGENT
// and below require clinician authorisation (a different source value,
// not yet needed since only the triage nurse's IMMEDIATE path exists).
public enum PaymentBypassSource {
    TRIAGE_IMMEDIATE
}
