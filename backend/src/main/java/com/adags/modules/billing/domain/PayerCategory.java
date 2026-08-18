package com.adags.modules.billing.domain;

// The pricing dimension — deliberately not shared.kernel.PayerType
// (CASH/INSURANCE), which is the coarse encounter-level split. NHIF is
// named explicitly because it's the dominant scheme here; PRIVATE_SCHEME
// buckets every other insurer until the insurance module (SchemeCoverageRule,
// a later phase) can price per named scheme.
public enum PayerCategory {
    CASH,
    NHIF,
    PRIVATE_SCHEME,
    CORPORATE,
    STAFF
}
