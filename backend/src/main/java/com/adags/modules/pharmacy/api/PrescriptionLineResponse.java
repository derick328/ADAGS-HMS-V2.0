package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineStatus;

import java.util.UUID;

public record PrescriptionLineResponse(
        UUID id,
        UUID prescriptionOrderId,
        UUID itemId,
        int quantityPrescribed,
        int quantityDispensed,
        int remaining,
        String dosageInstructions,
        PrescriptionLineStatus status,
        boolean substitutionAllowed,
        String statusReason
) {

    public static PrescriptionLineResponse from(PrescriptionLine line) {
        return new PrescriptionLineResponse(line.id(), line.prescriptionOrderId(), line.itemId(),
                line.quantityPrescribed(), line.quantityDispensed(), line.remaining(), line.dosageInstructions(),
                line.status(), line.substitutionAllowed(), line.statusReason());
    }
}
