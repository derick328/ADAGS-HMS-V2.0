package com.adags.modules.clinical;

import com.adags.shared.kernel.AllergySummary;
import com.adags.shared.kernel.WardVitalsResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Parameters are deliberately primitives/UUID/Instant/String —
// never a clinical-module domain type (VitalSignsContext, Avpu,
// AllergySource) — so a caller like encounters never needs to import
// com.adags.modules.clinical.domain, matching PatientFacade's precedent of
// returning shared/kernel vocabulary rather than its own domain types.
public interface ClinicalFacade {

    /**
     * Records one full TRIAGE-context set of vitals (06-triage.md §2.2/§2.3).
     * triageAssessmentId is the caller's TriageAssessment id, stored as
     * VitalSigns.context_ref_id. Evaluates each of the six core vitals
     * against the patient's current age-band VitalReferenceRange — throws
     * ImplausibleVitalException (rejected, names the bound) or
     * VitalReferenceRangeNotConfiguredException (unconfigured band) if a
     * reading fails that check; nothing is saved when it throws. avpu is
     * the raw Avpu enum name, or null. Returns the persisted VitalSigns id.
     */
    UUID recordTriageVitalSigns(
            UUID encounterId,
            UUID patientId,
            UUID triageAssessmentId,
            double temperatureC,
            double systolicMmHg,
            double diastolicMmHg,
            double pulseBpm,
            double respiratoryRate,
            double spo2Pct,
            double weightKg,
            double heightCm,
            String avpu,
            Double bloodGlucoseMmol,
            UUID recordedBy,
            Instant occurredAt
    );

    /**
     * 17-ward-nurse.md §4.1/§4.3: records one WARD_ROUNDS-context set of
     * vitals against an admission (contextRefId = admissionId), reusing
     * the same VitalSigns table and reference-range evaluation triage
     * uses — no second vitals table. painScore/intakeMl/outputMl are the
     * ward-specific additions (§4.4's fluid balance). Returns the saved
     * reading's id plus every breaching vital, so the caller can raise a
     * ward alert and suggest CRITICAL without re-evaluating anything.
     */
    WardVitalsResult recordWardVitalSigns(
            UUID encounterId,
            UUID patientId,
            UUID admissionId,
            double temperatureC,
            double systolicMmHg,
            double diastolicMmHg,
            double pulseBpm,
            double respiratoryRate,
            double spo2Pct,
            double weightKg,
            double heightCm,
            String avpu,
            Double bloodGlucoseMmol,
            Integer painScore,
            Integer intakeMl,
            Integer outputMl,
            UUID recordedBy,
            Instant occurredAt
    );

    /**
     * 17-ward-nurse.md §10: "Post-op observations write to VitalSigns, not
     * to a fourth set of columns" — POST_OP context, contextRefId = the
     * surgery order id. No ward-alert-equivalent escalation exists for
     * surgery yet (WardAlertNotification is inpatient-specific), so unlike
     * recordWardVitalSigns this returns just the id; an abnormal reading is
     * still snapshotted (abnormal flag + range id on the reading itself),
     * a caller just isn't notified of it today.
     */
    UUID recordPostOpVitalSigns(
            UUID encounterId,
            UUID patientId,
            UUID surgeryOrderId,
            double temperatureC,
            double systolicMmHg,
            double diastolicMmHg,
            double pulseBpm,
            double respiratoryRate,
            double spo2Pct,
            double weightKg,
            double heightCm,
            String avpu,
            Double bloodGlucoseMmol,
            UUID recordedBy,
            Instant occurredAt
    );

    /**
     * Appends an AllergyRecord to the patient — never an update (06-triage.md
     * §2.4: "written to the patient... so they persist to future visits").
     * source is the raw AllergySource enum name (e.g. "TRIAGE"). Returns the
     * new record's id.
     */
    UUID appendAllergyRecord(UUID patientId, UUID encounterId, String description, UUID recordedBy, String source);

    /**
     * 07-doctor.md §7.1/§10: creates a new OPEN ConsultationRecord for
     * (encounterId, authorId), or resumes the existing one if already open.
     * Returns its id.
     */
    UUID openConsultationRecord(UUID encounterId, UUID authorId);

    /**
     * 09-doctor-incomplete-consultations.md §5.1: validates the required
     * fields (history_of_presenting_illness, physical_examination, a
     * primary diagnosis, treatment_plan — final_diagnosis stays nullable,
     * §4.4) on the OPEN record for (encounterId, authorId) and marks it
     * FINALIZED. Throws InvalidConsultationRecordException (validation
     * failure) or NoSuchElementException (no OPEN record) — the caller's
     * encounter transition must not happen if this throws.
     */
    void finalizeConsultationRecord(UUID encounterId, UUID authorId, boolean retrospectiveCompletion, String retrospectiveNote);

    /**
     * 12-pharmacist.md §5.2, root CLAUDE.md "Safety": every allergy record
     * for the patient, every confirmation status, newest first —
     * UNCONFIRMED and REFUTED are returned identically to CONFIRMED. Free-
     * text allergies mean no automated matching is possible, so the
     * pharmacist (or doctor) is the human check, and hiding any record
     * removes that check entirely. Never filtered to "current" or "active"
     * — {@link AllergyRecordRepository#findByPatientId} is full history by
     * design.
     */
    List<AllergySummary> findAllergies(UUID patientId);

    /**
     * 12-pharmacist.md §5.2: "patient weight, for paediatric verification" —
     * the most recently recorded weight for this encounter, from whichever
     * VitalSigns context recorded it (TRIAGE, CONSULTATION, ...). Empty if
     * no vitals have been recorded on this encounter at all — the caller
     * shows nothing rather than a stale figure from a different visit.
     */
    Optional<Double> findLatestWeightKg(UUID encounterId);

    /** Latest explicit clinician answer for this encounter; empty means unanswered. */
    Optional<String> findPregnancyStatus(UUID encounterId);

    /** Whether the clinician should ask pregnancy status before prescribing. */
    boolean isPregnancyAssessmentApplicable(UUID patientId);
}
