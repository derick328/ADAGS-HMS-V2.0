package com.adags.modules.pharmacy.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionLineRepository {

    PrescriptionLine insert(PrescriptionLine line);

    Optional<PrescriptionLine> findById(UUID id);

    /** Every line under one prescription order — Request History and the patient timeline both need the order's line items, not just its header. */
    List<PrescriptionLine> findByPrescriptionOrderId(UUID prescriptionOrderId);

    /**
     * Persists quantityDispensed/status/statusReason from {@code line} —
     * called once per Dispense (materialised total, root CLAUDE.md "Data"),
     * and once per markUnavailable/cancelForSubstitution transition.
     */
    PrescriptionLine update(PrescriptionLine line);
}
