package com.adags.modules.inpatient.domain;

// 17-ward-nurse.md §5.1. An unmarked slot past scheduled_at is OVERDUE and
// visible — that's a derived view over DUE slots (scheduled_at < now), not
// a stored status of its own.
public enum MedicationAdministrationStatus {
    DUE,
    GIVEN,
    REFUSED,
    OMITTED,
    HELD
}
