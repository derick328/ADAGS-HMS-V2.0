package com.adags.modules.pharmacy.domain;

import java.util.UUID;

/** Original requested unit; prescription_line quantities remain base units. */
public record PrescriptionUnitSnapshot(UUID prescriptionLineId, double requestedQuantity,
                                       String requestedUnit, double conversionFactor, String baseUnit,
                                       String dosageForm, String productStrength) {
    public PrescriptionUnitSnapshot(UUID prescriptionLineId, double requestedQuantity,
                                    String requestedUnit, double conversionFactor, String baseUnit) {
        this(prescriptionLineId, requestedQuantity, requestedUnit, conversionFactor, baseUnit, null, null);
    }
}
