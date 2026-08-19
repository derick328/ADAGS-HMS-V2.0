package com.adags.modules.inpatient.api;

import jakarta.validation.constraints.NotBlank;

public record RecordWoundCareRequest(
        @NotBlank String appearance,
        boolean dressingChanged,
        String dressingType,
        boolean signsOfInfection,
        String infectionDescription,
        String remarks
) {
}
