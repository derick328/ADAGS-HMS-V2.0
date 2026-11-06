package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.Urgency;

import java.time.Instant;
import java.util.UUID;

public record LabOrderResponse(
        UUID id,
        UUID encounterId,
        UUID patientId,
        UUID labTestId,
        String clinicalIndication,
        String specialInstructions,
        Urgency urgency,
        UUID invoiceLineId,
        Instant orderedAt
) {

    public static LabOrderResponse from(LabOrder order) {
        return new LabOrderResponse(order.id(), order.encounterId(), order.patientId(), order.labTestId(),
                order.clinicalIndication(), order.specialInstructions(), order.urgency(), order.invoiceLineId(), order.orderedAt());
    }
}
