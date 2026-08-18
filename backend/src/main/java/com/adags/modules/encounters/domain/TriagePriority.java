package com.adags.modules.encounters.domain;

// 06-triage.md §2.8. Declaration order is the queue sort order (§3:
// "ORDER BY priority ASC" — IMMEDIATE, URGENT, LESS_URGENT, NON_URGENT),
// which is exactly Java enum ordinal order here, so callers may sort on
// ordinal() directly rather than a separate rank table.
public enum TriagePriority {
    IMMEDIATE,
    URGENT,
    LESS_URGENT,
    NON_URGENT
}
