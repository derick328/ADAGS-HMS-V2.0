package com.adags.modules.billing.domain;

// 02-billing-architecture.md §9.2: "CHEQUE lands in PENDING_CLEARANCE,
// not SETTLED." No clearance-transition service exists yet — a cheque
// payment simply stays PENDING_CLEARANCE, contributing nothing to the
// gate or to outstanding, until that lands.
public enum PaymentStatus {
    SETTLED,
    PENDING_CLEARANCE
}
