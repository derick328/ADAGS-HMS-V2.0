package com.adags.modules.inpatient.domain;

import java.util.Optional;
import java.util.UUID;

// Mirrors laboratory.domain.ClinicalLeadDirectory's role — duplicated, not
// imported, since it lives in laboratory.domain and is not facade-exposed
// (root backend/CLAUDE.md "Modules": no module imports another module's
// domain types). Facility-scoped: a facility-scoped or GLOBAL role
// assignment qualifies, same granularity as the lab original.
public interface StaffDirectory {

    /** §4.3/§6: escalation target for an unacknowledged ward alert — CRITICAL_RESULT_ESCALATION_RECEIVE holder. */
    Optional<UUID> findOneActiveClinicalLead(UUID facilityId);

    /** §5.4: DRUG_UNAVAILABLE notification target — a ward stockout nobody else will report. */
    Optional<UUID> findOneActivePharmacist(UUID facilityId);
}
