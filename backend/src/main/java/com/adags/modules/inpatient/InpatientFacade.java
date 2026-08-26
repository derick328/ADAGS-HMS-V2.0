package com.adags.modules.inpatient;

import com.adags.shared.kernel.WardSummary;

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
     * The ward this encounter's active admission currently occupies (id and
     * name only). Empty for an outpatient encounter, and for a patient who
     * is admitted but not yet assigned a bed. Read-side only — pharmacy's
     * dispensing queue groups its inpatient rows by ward.
     */
    Optional<WardSummary> findActiveAdmissionWard(UUID encounterId);

    /**
     * 17-ward-nurse.md §5.1: generates the MAR slots for one prescription
     * line. frequency is the raw MedicationFrequency enum name (STAT, OD,
     * BD, TDS, QDS, PRN — PRN generates nothing). Idempotent — calling
     * twice for the same line posts nothing new past the first call.
     */
    void generateMedicationAdministrationSchedule(
            UUID admissionId, UUID prescriptionLineId, String frequency, int durationDays, Instant startAt);

    /**
     * Phase 8 item 5: the surgery module's ADMIT_TO_WARD disposition.
     * Deliberately separate from AdmitPatientService (the doctor's own
     * IN_CONSULT admit path) — that service also finalises a
     * ConsultationRecord via ClinicalFacade, which doesn't apply to a
     * post-surgical admission. Creates the Admission row only
     * (WardStatus.ADMITTED, admittedAt = now — "bed-day accrual begins at
     * sent-to-admission timestamp, NOT procedure end," which this
     * satisfies simply by being called after COMPLETED, not at IN_PROGRESS
     * start). Does not assign a specific bed — that stays AssignBedService's
     * job, same as every other admission. orderedWardClass is the raw
     * WardClass enum name (GENERAL/PRIVATE); caller does not need to
     * import inpatient.domain (root CLAUDE.md "Modules"). Encounter
     * transition (IN_CONSULT -> ADMITTED) is the caller's own
     * responsibility via EncounterFacade.admit — this method only creates
     * the Admission row.
     */
    UUID admitFromProcedure(UUID encounterId, UUID patientId, UUID facilityId, UUID admittingDoctorId,
                             String orderedWardClass, UUID actorUserId);
}
