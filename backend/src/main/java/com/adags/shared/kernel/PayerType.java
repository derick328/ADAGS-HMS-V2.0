package com.adags.shared.kernel;

// Genuinely cross-module: resolved from a patient's insurance at encounter
// check-in (05-receptionist.md §5.2), consumed by the (not yet built)
// billing gate (02-billing-architecture.md §6.1). Lives here, not in
// patients or encounters, so neither module has to depend on the other for
// a plain vocabulary type.
public enum PayerType {
    CASH,
    INSURANCE
}
