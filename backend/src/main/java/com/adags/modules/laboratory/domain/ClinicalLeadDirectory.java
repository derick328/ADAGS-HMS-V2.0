package com.adags.modules.laboratory.domain;

import java.util.Optional;
import java.util.UUID;

// 15-lab-technician.md §7: "unacknowledged after N minutes -> escalate to
// CLINICAL_LEAD." Facility-scoped: a facility-scoped or GLOBAL
// CRITICAL_RESULT_ESCALATION_RECEIVE holder (V5's catalogue grants it to
// CLINICAL_LEAD only) qualifies — same FACILITY-scope granularity as
// SPECIMEN_ACCEPT/LAB_ORDER_CREATE elsewhere in this module.
public interface ClinicalLeadDirectory {

    /** One active CLINICAL_LEAD for this facility, if any — deterministic (earliest assignment), not "any." */
    Optional<UUID> findOneActiveClinicalLead(UUID facilityId);
}
