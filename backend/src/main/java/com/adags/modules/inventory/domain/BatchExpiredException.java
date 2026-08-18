package com.adags.modules.inventory.domain;

import java.util.UUID;

// 12-pharmacist.md §6.3/§7.3: "Expired batches HARD-BLOCKED from
// dispensing, not flagged on a report... Relying on a pharmacist noticing
// a date under time pressure is not a control." Thrown for a batch that is
// past its expiry_date OR whose status is anything other than ACTIVE
// (QUARANTINED/EXPIRED/WRITTEN_OFF) — checked live against the current
// date at the moment of dispensing, not only against the stored status
// column, since nothing in this build brief runs a scheduled job to flip
// status the instant a batch expires.
public class BatchExpiredException extends RuntimeException {

    private final UUID batchId;

    public BatchExpiredException(UUID batchId) {
        super("Batch " + batchId + " is expired or otherwise not dispensable.");
        this.batchId = batchId;
    }

    public UUID batchId() {
        return batchId;
    }
}
