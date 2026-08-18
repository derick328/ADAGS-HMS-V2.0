package com.adags.modules.encounters.application;

// 05-receptionist.md §5.3: "Registered in error" requires a reason.
public class MissingCloseReasonException extends RuntimeException {

    public MissingCloseReasonException() {
        super("A reason is required to cancel an encounter registered in error.");
    }
}
