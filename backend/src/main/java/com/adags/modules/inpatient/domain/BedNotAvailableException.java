package com.adags.modules.inpatient.domain;

import java.util.UUID;

// root CLAUDE.md "Concurrency": surfaces idx_bed_assignment_one_active_per_bed
// rejecting a double assignment — the loser of the race gets this, not a 500.
// Same treatment as inventory's InsufficientStockException.
public class BedNotAvailableException extends RuntimeException {

    private final UUID bedId;

    public BedNotAvailableException(UUID bedId) {
        super("Bed is already assigned: " + bedId);
        this.bedId = bedId;
    }

    public UUID bedId() {
        return bedId;
    }
}
