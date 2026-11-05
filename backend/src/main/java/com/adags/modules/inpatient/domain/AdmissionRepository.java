package com.adags.modules.inpatient.domain;

import com.adags.shared.kernel.WardSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionRepository {

    Optional<Admission> findById(UUID id);

    Optional<Admission> findByEncounterId(UUID encounterId);

    Admission insert(Admission admission);

    /** Admissions with a currently active (unreleased) BedAssignment — the nightly accrual job's candidate set. */
    List<Admission> findAllWithActiveBedAssignment();

    /**
     * 17-ward-nurse.md §3: "Scope the ward patient list" — admissions whose
     * currently active BedAssignment sits in wardId. Includes an admission
     * with discharge_ordered_at set but not yet actually_departure_at (the
     * bed is still occupied until the bed assignment is released), same as
     * findAllWithActiveBedAssignment's join.
     */
    List<Admission> findByCurrentWardId(UUID wardId);

    /**
     * 17-ward-nurse.md §8: admissions in this facility that are active
     * (actual_departure_at IS NULL) but have NO currently active bed
     * assignment — a patient the doctor marked ADMITTED who has not yet been
     * placed into a ward bed. These appear in no ward's roster
     * ({@link #findByCurrentWardId} requires an active bed assignment) and
     * accrue no bed-days, so without this they are invisible to every nurse
     * and to billing. It is the claim/placement worklist's source: a nurse
     * who can assign beds in the facility pulls one into her ward.
     */
    List<Admission> findAwaitingPlacement(UUID facilityId);

    /**
     * 07-doctor.md §7/§11: the admitting doctor's own currently-admitted
     * patients — active admissions (actual_departure_at IS NULL) this doctor
     * ordered, whether or not they have been placed into a bed yet. The
     * doctor's "Admitted" worklist; ordered oldest-admitted first.
     */
    List<Admission> findActiveByAdmittingDoctor(UUID admittingDoctorId);

    /**
     * The ward this encounter's active admission currently occupies — its
     * unreleased BedAssignment's bed's ward, id and name only. Empty when
     * the encounter has no active admission (outpatient, or discharged with
     * actual_departure_at set) or has one that is not yet bed-assigned.
     * The same join as {@link #findByCurrentWardId}, read from the encounter
     * end; a read projection, not an aggregate — the one caller (pharmacy's
     * dispensing queue via InpatientFacade) needs only these two fields.
     */
    Optional<WardSummary> findCurrentWardByEncounterId(UUID encounterId);

    /**
     * 04-procedure-bedday-rules.md §3.6: sets discharge_ordered_at/by/role.
     * @throws StaleAdmissionException if the row's version does not match expectedVersion.
     */
    Admission orderDischarge(UUID admissionId, Instant dischargeOrderedAt, UUID dischargeOrderedBy,
                              DischargeOrdererRole dischargeOrdererRole, int expectedVersion);

    /**
     * §3.9: clears discharge_ordered_at/by/role — "billing resumes
     * continuously from admission" falls out for free, since the accrual
     * job never paused for an ordered-but-not-yet-completed discharge in
     * the first place (it tracks bed occupancy, not this field).
     * @throws StaleAdmissionException if the row's version does not match expectedVersion.
     */
    Admission reverseDischargeOrder(UUID admissionId, int expectedVersion);

    /**
     * §3.6: "BedAssignment.released_at frees the bed (actual departure)" —
     * this is the admission's own record of that same moment, independent
     * of which BedAssignment happened to be active at the time.
     * @throws StaleAdmissionException if the row's version does not match expectedVersion.
     */
    Admission recordActualDeparture(UUID admissionId, Instant actualDepartureAt, int expectedVersion);

    /**
     * 17-ward-nurse.md §9: the nurse's own confirming action — sets
     * ward_status and clears any pending CRITICAL suggestion, whether she
     * agrees with it or not. Not version-guarded: ward status is a
     * frequently-updated nursing observation, not a financial or clinical
     * record needing optimistic-lock protection.
     */
    Admission updateWardStatus(UUID admissionId, WardStatus wardStatus, Instant updatedAt, UUID updatedBy);

    /** §4.3: a breaching vital proposes CRITICAL — this alone never changes ward_status. */
    Admission suggestCriticalStatus(UUID admissionId, Instant suggestedAt);
}
