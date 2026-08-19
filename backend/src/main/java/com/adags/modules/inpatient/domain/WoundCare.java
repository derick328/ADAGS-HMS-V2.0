package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.UUID;

// 17-ward-nurse.md §6: fields as described in the spec, no changes needed
// there. signsOfInfection = true is what RecordWoundCareService turns into
// a WardAlertNotification — "a wound infection noticed at 22:00 that waits
// for the morning round is a preventable escalation."
public record WoundCare(
        UUID id,
        UUID admissionId,
        String appearance,
        boolean dressingChanged,
        String dressingType,
        boolean signsOfInfection,
        String infectionDescription,
        String remarks,
        UUID recordedBy,
        Instant recordedAt
) {

    public static WoundCare record(UUID admissionId, String appearance, boolean dressingChanged, String dressingType,
                                    boolean signsOfInfection, String infectionDescription, String remarks, UUID recordedBy) {
        if (appearance == null || appearance.isBlank()) {
            throw new IllegalArgumentException("appearance is required.");
        }
        if (signsOfInfection && (infectionDescription == null || infectionDescription.isBlank())) {
            throw new IllegalArgumentException("infectionDescription is required when signsOfInfection is true.");
        }
        return new WoundCare(null, admissionId, appearance, dressingChanged, dressingType,
                signsOfInfection, infectionDescription, remarks, recordedBy, null);
    }
}
