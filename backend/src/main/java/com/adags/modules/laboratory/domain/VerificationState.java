package com.adags.modules.laboratory.domain;

// 16-lab-verification-model.md v0.2 §2: the verification axis, independent
// of release — a result can sit at PENDING indefinitely with no queue to
// clear it (no role holds LAB_RESULT_VERIFY yet) and that is not a failure
// state (v0.2 §3.3). LAPSED/quality-audit sampling (v0.2 §3) are the
// retrospective-verification workflow, not built in this slice — nothing
// currently transitions a result into LAPSED.
public enum VerificationState {
    NOT_REQUIRED,
    PENDING,
    VERIFIED,
    AMENDED,
    LAPSED
}
