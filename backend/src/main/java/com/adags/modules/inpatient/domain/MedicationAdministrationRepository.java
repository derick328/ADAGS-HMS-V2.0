package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationAdministrationRepository {

    /**
     * Bulk-inserts DUE slots, ON CONFLICT (prescription_line_id,
     * scheduled_at) DO NOTHING — generation must not double-post if called
     * twice for the same line (root backend/CLAUDE.md "Concurrency"), the
     * real guard being the partial unique index, not this repository.
     */
    void insertSchedule(List<MedicationAdministration> slots);

    Optional<MedicationAdministration> findById(UUID id);

    List<MedicationAdministration> findByAdmissionId(UUID admissionId);

    /** 17-ward-nurse.md §11: DUE slots already past their scheduled time — the "overdue MAR" view. */
    List<MedicationAdministration> findOverdueByAdmissionId(UUID admissionId, Instant now);

    /**
     * The compare-and-swap mark: {@code UPDATE ... WHERE status = 'DUE'}.
     * Returns the updated row, or empty if the slot was not DUE (already
     * marked by a concurrent or repeated request) — the caller turns that
     * into MedicationAdministrationNotDueException.
     */
    Optional<MedicationAdministration> mark(MedicationAdministration marked);
}
