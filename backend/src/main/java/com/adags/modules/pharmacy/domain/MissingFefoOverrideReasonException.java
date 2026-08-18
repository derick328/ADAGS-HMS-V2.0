package com.adags.modules.pharmacy.domain;

// 12-pharmacist.md §5.3: "FEFO pre-selected, override with a reason. Free
// choice means fresh stock goes out while near-expiry stock sits and later
// expires." Thrown when the submitted batch selection differs from the
// FEFO suggestion and any submitted line lacks a reason.
public class MissingFefoOverrideReasonException extends RuntimeException {

    public MissingFefoOverrideReasonException() {
        super("Batch selection differs from the FEFO suggestion — every dispense line requires an override reason.");
    }
}
