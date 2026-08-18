package com.adags.modules.pharmacy.domain;

import java.util.Optional;
import java.util.UUID;

public interface PrescriptionLineRepository {

    PrescriptionLine insert(PrescriptionLine line);

    Optional<PrescriptionLine> findById(UUID id);

    /**
     * Persists quantityDispensed/status/statusReason from {@code line} —
     * called once per Dispense (materialised total, root CLAUDE.md "Data"),
     * and once per markUnavailable/cancelForSubstitution transition.
     */
    PrescriptionLine update(PrescriptionLine line);
}
