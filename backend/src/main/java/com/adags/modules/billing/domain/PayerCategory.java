package com.adags.modules.billing.domain;

import com.adags.shared.kernel.PayerType;

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
    STAFF;

    /**
     * Same simplification BillingFacadeImpl's charge-posting paths already
     * make: INSURANCE -&gt; NHIF, pending per-scheme pricing. For billing-
     * internal callers resolving a category from an encounter's coarse
     * payer type (invoice creation, the price-catalog search) — not part
     * of BillingFacade since both callers already live in this module.
     */
    public static PayerCategory fromPayerType(PayerType payerType) {
        return payerType == PayerType.CASH ? CASH : NHIF;
    }
}
