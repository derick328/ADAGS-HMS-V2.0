package com.adags.modules.pharmacy.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispenseRepository {

    Dispense insert(Dispense dispense);

    Optional<Dispense> findById(UUID id);

    /** §5.4/§5.5: a repeat with the same key returns the original, never a second dispense — same PostPaymentService pattern. */
    Optional<Dispense> findByIdempotencyKey(String idempotencyKey);

    /** 17-ward-nurse.md §5.2: every dispense recorded against this line — what MAR administration's batch link reads from. */
    List<Dispense> findByPrescriptionLineId(UUID prescriptionLineId);

    /** 12-pharmacist.md §11: "My dispenses" — this pharmacist's own work, newest first. */
    List<Dispense> findByDispensedBy(UUID dispensedBy);

    /** 12-pharmacist.md §11: "Patient lookup" — every dispense against this patient, regardless of pharmacist, newest first. Joined through prescription_line/prescription_order, both this module's own tables. */
    List<Dispense> findByPatientId(UUID patientId);

    /** Every patient with at least one dispense on record — the candidate set the patient-lookup search query is filtered against, since this module cannot search patients directly (root CLAUDE.md "Modules"). */
    List<UUID> findDistinctDispensedPatientIds();
}
