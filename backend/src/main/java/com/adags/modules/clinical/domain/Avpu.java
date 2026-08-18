package com.adags.modules.clinical.domain;

// 06-triage.md §7.3: level of consciousness, not yet ruled on as a required
// field — stored nullable per the item-2 instruction ("adding columns to a
// vitals table later means backfilling nulls across every reading").
public enum Avpu {
    ALERT,
    VOICE,
    PAIN,
    UNRESPONSIVE
}
