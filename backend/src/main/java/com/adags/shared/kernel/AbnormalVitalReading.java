package com.adags.shared.kernel;

// One breaching reading from a ward vitals recording — vitalType is the
// raw VitalType enum name (same treatment ClinicalFacade gives Avpu across
// this boundary).
public record AbnormalVitalReading(String vitalType, double value) {
}
