package com.adags.shared.kernel;

import java.util.List;
import java.util.UUID;

// 17-ward-nurse.md §4.3: what ClinicalFacade.recordWardVitalSigns hands
// back to inpatient — the saved reading's id plus every breaching vital,
// so the caller can raise a WardAlertNotification and suggest CRITICAL
// without re-evaluating anything itself.
public record WardVitalsResult(UUID vitalSignsId, List<AbnormalVitalReading> abnormalReadings) {

    public boolean hasAbnormalReading() {
        return !abnormalReadings.isEmpty();
    }
}
