package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.PrescriptionLineStatus;

import java.time.Instant;
import java.util.UUID;

// One flat row per open, currently authorized prescription line. Billing
// remains per line, so a partly paid order contributes only its paid lines.
public record PrescriptionQueueRowResponse(
        UUID prescriptionLineId,
        UUID prescriptionOrderId,
        UUID encounterId,
        UUID patientId,
        String patientName,
        String hospitalNumber,
        UUID itemId,
        String itemCode,
        String itemName,
        int quantityPrescribed,
        int quantityDispensed,
        int remaining,
        String dosageInstructions,
        PrescriptionLineStatus status,
        Instant prescribedAt,
        boolean gateSatisfied,
        boolean isEmergencyBypass,
        boolean isInpatient,
        // 12-pharmacist.md §9.1/§12: the item's catalogue descriptors, so
        // the Dispensing Queue can filter by medication type without a
        // client-side lookup per row. Nullable (UNCLASSIFIED item).
        String itemClassification,
        String itemForm,
        // The ward the encounter's active admission currently occupies —
        // null for an outpatient line and for an inpatient not yet given a
        // bed. Lets the queue group/filter its inpatient rows by ward.
        UUID wardId,
        String wardName
) {
}
