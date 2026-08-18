package com.adags.modules.billing.domain;

// 02-billing-architecture.md §3.2: one record covering every way a line
// becomes fulfillable. Replaces the emergency bar spec's separate
// posted_under_bypass_id — two records for one fact would eventually
// disagree.
public enum AuthorizationType {
    PAYMENT,
    INSURANCE,
    EMERGENCY_BYPASS,
    WAIVER,
    DEPOSIT
}
