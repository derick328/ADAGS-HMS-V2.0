package com.adags.modules.clinical.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllergyRecordRepository {

    /** Never an update to the clinical content — append-only, see AllergyRecord. */
    AllergyRecord insert(AllergyRecord allergyRecord);

    Optional<AllergyRecord> findById(UUID id);

    /** Full history, newest first — the doctor and pharmacist screens render every entry, not just the latest. */
    List<AllergyRecord> findByPatientId(UUID patientId);

    /** 07-doctor.md §6.1: updates confirmation metadata in place — see AllergyRecord's class comment. */
    void reconcile(UUID id, AllergyConfirmationStatus decision, UUID confirmedBy, Instant confirmedAt, String confirmationNote);
}
