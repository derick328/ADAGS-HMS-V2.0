package com.adags.modules.billing.domain;

// 02-billing-architecture.md §5.3: "The attending doctor revokes once the
// patient is stabilised." Revocation isn't wired to any caller yet — no
// doctor module exists — but the status is modelled now so the column
// never needs a later rename (same reasoning as EncounterState modelling
// the full transition graph ahead of every caller existing).
public enum PaymentBypassStatus {
    ACTIVE,
    REVOKED
}
