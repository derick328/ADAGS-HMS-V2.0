package com.adags.modules.inpatient.application;

import java.util.UUID;

public record RecordWoundCareCommand(
        UUID admissionId,
        String appearance,
        boolean dressingChanged,
        String dressingType,
        boolean signsOfInfection,
        String infectionDescription,
        String remarks,
        UUID recordedBy
) {
}
