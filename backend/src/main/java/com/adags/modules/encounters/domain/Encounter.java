package com.adags.modules.encounters.domain;

import com.adags.shared.kernel.PayerType;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;

import java.time.Instant;
import java.util.UUID;

// Carries facility_id; Patient does not (root CLAUDE.md "Facility") — the
// encounter happened at a place, the patient is a person and is shared
// across facilities (05-receptionist.md, 21-multi-facility.md §3).
public record Encounter(
        UUID id,
        UUID patientId,
        UUID facilityId,
        UUID departmentId,
        /** Null for a walk-in — 05-receptionist.md ⚠7. No FK: scheduling module doesn't exist yet. */
        UUID appointmentId,
        /** Null until triage assigns a referral — 06-triage.md §5: never blank once triage submits. */
        UUID assignedProviderId,
        EncounterType type,
        EncounterState state,
        PayerType payerType,
        /** backend/CLAUDE.md "Optimistic locking (@Version) on Encounter" — see JdbcEncounterRepository. */
        int version,
        Instant createdAt,
        UUID createdBy
) {
    public ScopeRef scope() {
        return departmentId == null
                ? ScopeRef.of(ScopeType.FACILITY, facilityId)
                : ScopeRef.of(ScopeType.DEPARTMENT, departmentId);
    }
}
