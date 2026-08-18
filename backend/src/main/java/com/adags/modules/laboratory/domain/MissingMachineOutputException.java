package com.adags.modules.laboratory.domain;

// 16-lab-verification-model.md v0.2 §4: "You cannot verify a result
// retrospectively without the analyser's original output." Mandatory
// whenever the test's catalogue entry says produces_machine_output=true —
// a result entered without it is permanently unverifiable, not just
// incomplete.
public class MissingMachineOutputException extends RuntimeException {

    public MissingMachineOutputException(String testCode) {
        super("Machine output capture is required for " + testCode
                + " (produces_machine_output=true) — a result entered without it can never be verified.");
    }
}
