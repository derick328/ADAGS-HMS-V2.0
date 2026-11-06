package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.EncounterType;
import com.adags.shared.kernel.PayerType;

import java.time.Instant;
import java.util.UUID;

public record EncounterResponse(
        UUID id,
        UUID patientId,
        UUID facilityId,
        UUID departmentId,
        UUID appointmentId,
        UUID assignedProviderId,
        EncounterType type,
        EncounterState state,
        PayerType payerType,
        int version,
        Instant createdAt,
        boolean existingEncounter,
        String clinicianName
) {
    public static EncounterResponse from(Encounter encounter) { return from(encounter, false); }
    public EncounterResponse withClinicianName(String name) {
        return new EncounterResponse(id, patientId, facilityId, departmentId, appointmentId,
                assignedProviderId, type, state, payerType, version, createdAt, existingEncounter, name);
    }
    public static EncounterResponse from(Encounter encounter, boolean existingEncounter) {
        return new EncounterResponse(
                encounter.id(), encounter.patientId(), encounter.facilityId(), encounter.departmentId(),
                encounter.appointmentId(), encounter.assignedProviderId(), encounter.type(), encounter.state(),
                encounter.payerType(), encounter.version(), encounter.createdAt(), existingEncounter, null);
    }
}
