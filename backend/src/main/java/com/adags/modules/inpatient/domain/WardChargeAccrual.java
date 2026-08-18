package com.adags.modules.inpatient.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 04-procedure-bedday-rules.md §3.5. One row per occupied bed-day,
// accrualBoundaryAt being the 10:00 timestamp it was posted at.
// dailyRate is Ward.dailyRate snapshotted at posting time (root CLAUDE.md
// "Data": a value that can change underneath a record) — the row stays
// correct even if the ward's configured rate changes later.
//
// invoiceLineId is null only transiently, between reserving this row (which
// claims the (bedAssignmentId, accrualBoundaryAt) slot via the DB's unique
// index) and the ChargePostingService call completing — both happen inside
// one @Transactional, so a committed row is never left without one.
public record WardChargeAccrual(
        UUID id,
        UUID admissionId,
        UUID bedAssignmentId,
        UUID wardId,
        Instant accrualBoundaryAt,
        Money dailyRate,
        UUID invoiceLineId,
        Instant createdAt
) {
}
