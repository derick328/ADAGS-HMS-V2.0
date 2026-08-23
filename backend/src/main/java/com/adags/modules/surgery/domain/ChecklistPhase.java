package com.adags.modules.surgery.domain;

// 04-*.md §7: SIGN_IN blocks anaesthesia start, TIME_OUT blocks IN_PROGRESS,
// SIGN_OUT blocks COMPLETED — each name matches the WHO Surgical Safety
// Checklist pause it represents.
public enum ChecklistPhase {
    SIGN_IN,
    TIME_OUT,
    SIGN_OUT
}
