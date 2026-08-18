package com.adags.modules.inpatient.domain;

// 04-procedure-bedday-rules.md §3.9: "Cancel discharge order -> reason
// required." Without a reason, staff would delete the record and
// re-admit instead — fragmenting one encounter into two.
public class MissingReversalReasonException extends RuntimeException {

    public MissingReversalReasonException() {
        super("A reason is required to reverse a discharge order.");
    }
}
