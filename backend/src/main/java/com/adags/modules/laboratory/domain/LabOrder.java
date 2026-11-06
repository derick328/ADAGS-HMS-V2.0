package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.UUID;

// 15-lab-technician.md §4/§12: one row per ordered test (see
// V21__lab_order_and_specimen.sql for why this isn't a multi-test panel).
// invoiceLineId is set at creation — the charge is posted in the same
// transaction that creates the order, same pattern as the consultation
// charge posted alongside TriageCompleted.
public record LabOrder(
        UUID id,
        UUID encounterId,
        UUID patientId,
        UUID facilityId,
        UUID labTestId,
        UUID orderedBy,
        Instant orderedAt,
        String clinicalIndication,
        String specialInstructions,
        Urgency urgency,
        UUID invoiceLineId,
        Instant createdAt
) {

    public static LabOrder create(UUID encounterId, UUID patientId, UUID facilityId, UUID labTestId, UUID orderedBy,
                                   String clinicalIndication, String specialInstructions, Urgency urgency, UUID invoiceLineId) {
        return new LabOrder(null, encounterId, patientId, facilityId, labTestId, orderedBy, null,
                clinicalIndication, specialInstructions, urgency, invoiceLineId, null);
    }

    /** Pre-generated id, so the charge posted before insert (sourceEventId) can point at this order. */
    public LabOrder withId(UUID id) {
        return new LabOrder(id, encounterId, patientId, facilityId, labTestId, orderedBy, orderedAt,
                clinicalIndication, specialInstructions, urgency, invoiceLineId, createdAt);
    }

    public LabOrder withInvoiceLineId(UUID invoiceLineId) {
        return new LabOrder(id, encounterId, patientId, facilityId, labTestId, orderedBy, orderedAt,
                clinicalIndication, specialInstructions, urgency, invoiceLineId, createdAt);
    }
}
