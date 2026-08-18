package com.adags.modules.clinical.domain;

// 07-doctor.md §6.1: "UNCONFIRMED and REFUTED are treated identically
// everywhere. Both display, both warn, neither is filtered from any
// downstream view. Only the label differs." The confirm/refute action is
// documentary, not functional — it never changes what a prescribing or
// dispensing screen shows.
public enum AllergyConfirmationStatus {
    UNCONFIRMED,
    CONFIRMED,
    REFUTED
}
