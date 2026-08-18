package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.UUID;

// 17-ward-nurse.md §8.1: "Beds are rows, not free text" — a bed a nurse can
// actually be assigned to, guarded by the partial unique index on
// BedAssignment, not a text field two patients could collide on.
public record Bed(
        UUID id,
        UUID wardId,
        String label,
        String bedType,
        BedStatus status,
        Instant createdAt
) {
}
