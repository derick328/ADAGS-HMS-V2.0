package com.adags.modules.pharmacy.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §5.2/§5.4/§12: one dispensing transaction against one
// PrescriptionLine, possibly split across several DispenseLine batch rows
// (§4.3) and possibly partial (§4.4). idempotencyKey: a double-tap on
// confirm must not decrement stock twice or chart two dispenses (root
// CLAUDE.md "Concurrency").
public record Dispense(
        UUID id,
        UUID prescriptionLineId,
        UUID dispensedBy,
        Instant dispensedAt,
        String idempotencyKey,
        int quantityDispensed,
        String counsellingNotes,
        String dispensingNotes
) {

    public static Dispense create(UUID prescriptionLineId, UUID dispensedBy, String idempotencyKey,
                                   int quantityDispensed, String counsellingNotes, String dispensingNotes) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required.");
        }
        if (quantityDispensed <= 0) {
            throw new IllegalArgumentException("quantityDispensed must be > 0.");
        }
        return new Dispense(null, prescriptionLineId, dispensedBy, null, idempotencyKey, quantityDispensed,
                counsellingNotes, dispensingNotes);
    }
}
