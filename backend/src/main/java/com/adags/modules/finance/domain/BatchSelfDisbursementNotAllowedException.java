package com.adags.modules.finance.domain;

// root CLAUDE.md "Authorisation": "CHECK (generated_by != disbursed_by)"
// — the database CHECK (V49) is the real guard; this is the friendly
// application-layer rejection before it ever reaches that constraint.
public class BatchSelfDisbursementNotAllowedException extends RuntimeException {

    public BatchSelfDisbursementNotAllowedException() {
        super("A payroll batch cannot be disbursed by the person who generated it.");
    }
}
