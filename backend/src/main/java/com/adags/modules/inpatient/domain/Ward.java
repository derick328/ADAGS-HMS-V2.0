package com.adags.modules.inpatient.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 17-ward-nurse.md §8.1. The `ward` table predates this module (created in
// V1__foundation.sql for shared/authz's WARD scope-hierarchy walk) — this
// is the domain shape V31__ward_bed_admission.sql brings the table in line
// with. code/active are the table's pre-existing columns, kept: a short
// identifier alongside name matches department/facility's own convention,
// and active is the soft-deactivation flag root CLAUDE.md "Data" requires
// instead of a hard delete.
public record Ward(
        UUID id,
        UUID facilityId,
        UUID departmentId,
        String code,
        String name,
        WardClass wardClass,
        GenderRestriction genderRestriction,
        Money dailyRate,
        boolean active,
        Instant createdAt
) {
}
