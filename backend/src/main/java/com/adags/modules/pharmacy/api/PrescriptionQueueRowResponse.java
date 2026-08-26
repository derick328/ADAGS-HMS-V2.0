package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.PrescriptionLineStatus;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §4: one flat row per open prescription line, everything
// the Dashboard's six queues need to bucket and count it. The frontend
// buckets client-side (ready / partially payable / awaiting payment /
// emergency / inpatient — surgery items are deferred with the surgery
// module and carry no row here), same "server returns the data, client
// owns the tab split" pattern as every other QueueShell screen
// (labQueueBuckets.ts). "Partially payable" is a property of the
// *order*, not this line — the frontend groups rows by
// prescriptionOrderId to tell the two apart, since gating itself stays
// per-line (root CLAUDE.md "Billing": "gating is per invoice line, never
// per invoice").
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
