package com.adags.modules.inpatient.domain;

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
}
