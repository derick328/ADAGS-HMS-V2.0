package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.UUID;

// root CLAUDE.md "Concurrency": surfaces idx_ward_charge_accrual_unique
// rejecting a second reservation for the same (bedAssignmentId,
// accrualBoundaryAt) — a rerun after a failure, or a genuinely concurrent
// job invocation, loses the race here rather than double-billing.
public class DuplicateAccrualException extends RuntimeException {

    public DuplicateAccrualException(UUID bedAssignmentId, Instant accrualBoundaryAt) {
        super("Bed-day already accrued for bed assignment " + bedAssignmentId + " at boundary " + accrualBoundaryAt);
    }
}
