package com.adags.modules.pharmacy.application;

import java.util.UUID;

public record PlacePrescriptionCommand(
        UUID encounterId,
        UUID itemId,
        int quantityPrescribed,
        String dosageInstructions,
        boolean substitutionAllowed,
        UUID prescribedBy
) {
}
