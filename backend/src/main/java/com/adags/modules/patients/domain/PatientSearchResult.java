package com.adags.modules.patients.domain;

import java.time.LocalDate;
import java.util.UUID;

// 05-receptionist.md §5.1 Panel B columns, minus "last visit date" and
// "currently open encounter" — those need the encounters module and are
// deferred to an EncounterFacade rather than pulled in here (patients
// cannot depend on encounters; see backend CLAUDE.md module boundary rule).
public record PatientSearchResult(
        UUID id,
        String hospitalNo,
        String fullName,
        LocalDate dateOfBirth,
        boolean dobIsEstimated,
        String phone
) {
}
