package com.adags.modules.laboratory.domain;

// 16-lab-verification-model.md v0.2 §9: snapshotted onto LabResult at entry
// time in phase 5 — raising a test's level later must not retroactively
// invalidate results released under the old rule. NONE is the default;
// with every test at NONE, behaviour is "enter, release, done," no queue.
public enum VerificationLevel {
    NONE,
    SELF,
    SECOND_PERSON,
    SPECIALIST
}
