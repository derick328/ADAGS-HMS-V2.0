package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.WardClass;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdmitPatientRequest(
        @NotNull UUID encounterId,
        @NotNull WardClass orderedWardClass,
        boolean retrospectiveCompletion,
        String retrospectiveNote
) {
}
