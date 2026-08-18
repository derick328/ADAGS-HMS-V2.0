package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.UUID;

// 15-lab-technician.md §5.5: stockBatchId is a documented hook, not a real
// FK — the inventory module doesn't exist yet. Free text (note) remains
// the supplementary channel until it does.
public record LabResultReagent(
        UUID id,
        UUID resultId,
        UUID stockBatchId,
        Double quantityUsed,
        String note,
        Instant createdAt
) {

    public static LabResultReagent create(UUID resultId, UUID stockBatchId, Double quantityUsed, String note) {
        return new LabResultReagent(null, resultId, stockBatchId, quantityUsed, note, null);
    }
}
