package com.adags.modules.pharmacy.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionLineRepository {

    void insertUnitSnapshot(PrescriptionUnitSnapshot snapshot);

    java.util.Optional<PrescriptionUnitSnapshot> findUnitSnapshot(java.util.UUID prescriptionLineId);

    PrescriptionLine insert(PrescriptionLine line);

    Optional<PrescriptionLine> findById(UUID id);

    Optional<PrescriptionLine> findByIdForUpdate(UUID id);

    /** Every line under one prescription order — Request History and the patient timeline both need the order's line items, not just its header. */
    List<PrescriptionLine> findByPrescriptionOrderId(UUID prescriptionOrderId);

    /** 12-pharmacist.md §4: every line still AWAITING_DISPENSE or PARTIALLY_DISPENSED, across the whole facility — the Dispense Queue's single source list, oldest first. */
    List<PrescriptionLine> findOpen();

    /**
     * Persists quantityDispensed/status/statusReason from {@code line} —
     * called once per Dispense (materialised total, root CLAUDE.md "Data"),
     * and once per markUnavailable/cancelForSubstitution transition.
     */
    PrescriptionLine update(PrescriptionLine line);
}
