package com.adags.modules.pharmacy.api;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §11: one row per Dispense for the History screen's two
// tabs — "My dispenses" (GET /api/dispenses/mine) and "Patient lookup"
// (GET /api/dispenses/patient-search). Same shape either way; the tabs
// differ only in which query populated the list.
public record DispenseHistoryResponse(
        UUID dispenseId,
        UUID prescriptionLineId,
        UUID prescriptionOrderId,
        UUID encounterId,
        UUID patientId,
        String patientName,
        String hospitalNumber,
        String itemCode,
        String itemName,
        int quantityDispensed,
        Instant dispensedAt,
        UUID dispensedBy,
        String dispensedByUsername,
        String counsellingNotes,
        String dispensingNotes
) {
}
