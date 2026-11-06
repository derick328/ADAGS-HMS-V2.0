package com.adags.modules.pharmacy.domain;

import java.util.UUID;

/** Original requested unit; prescription_line quantities remain base units. */
public record PrescriptionUnitSnapshot(UUID prescriptionLineId, int requestedQuantity,
                                       String requestedUnit, int conversionFactor, String baseUnit) {
}
