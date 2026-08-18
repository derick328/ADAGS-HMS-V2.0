package com.adags.modules.laboratory.domain;

// 16-lab-verification-model.md §5 (v0.1, unchanged): "Reason required."
public class MissingAmendmentReasonException extends RuntimeException {

    public MissingAmendmentReasonException() {
        super("An amendment requires a reason.");
    }
}
