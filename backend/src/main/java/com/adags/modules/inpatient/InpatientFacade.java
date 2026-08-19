package com.adags.modules.inpatient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Built first for pharmacy's PlacePrescriptionService — a ward
// prescription needs a MAR schedule generated the moment it's placed
// (17-ward-nurse.md §5.1), and pharmacy is the only caller that knows a
// prescription line and a frequency/duration at the same time. Never a
// com.adags.modules.inpatient.domain type across this boundary.
public interface InpatientFacade {

    /** True admission only — a discharged (actual_departure_at set) admission for this encounter doesn't count. */
    Optional<UUID> findActiveAdmissionId(UUID encounterId);

    /**
     * 17-ward-nurse.md §5.1: generates the MAR slots for one prescription
     * line. frequency is the raw MedicationFrequency enum name (STAT, OD,
     * BD, TDS, QDS, PRN — PRN generates nothing). Idempotent — calling
     * twice for the same line posts nothing new past the first call.
     */
    void generateMedicationAdministrationSchedule(
            UUID admissionId, UUID prescriptionLineId, String frequency, int durationDays, Instant startAt);
}
