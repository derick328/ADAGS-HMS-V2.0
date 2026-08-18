package com.adags.modules.laboratory.domain;

// 15-lab-technician.md §5.2: "flag ... Override permitted with a reason."
public class MissingOverrideReasonException extends RuntimeException {

    public MissingOverrideReasonException(String parameterCode) {
        super("A reason is required to override the computed flag for " + parameterCode + ".");
    }
}
