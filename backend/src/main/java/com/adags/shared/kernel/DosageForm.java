package com.adags.shared.kernel;

import java.util.Locale;

/** Supported medicine presentations and their supply-quantity policy. */
public enum DosageForm {
    CAPSULE, TABLET, TABLETS, ORAL_TABLETS, CAPLET, SYRUP, SUSPENSION, ORAL_SUSPENSION,
    INJECTION, CREAM, GEL, LOTION, PASTE;

    public static DosageForm fromCatalogue(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalized.endsWith("S") && !normalized.equals("TABLETS") && !normalized.equals("ORAL_TABLETS")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException unknown) {
            return null; // Existing, unrecognized presentations retain their legacy workflow.
        }
    }

    public static DosageForm requireSupported(String value) {
        DosageForm form = fromCatalogue(value);
        if (form == null) throw new IllegalArgumentException("Select a supported dosage form.");
        return form;
    }

    public boolean automaticQuantity() {
        return this == CAPSULE || this == TABLET || this == TABLETS || this == ORAL_TABLETS || this == CAPLET;
    }

    public String baseUnit() {
        return this == TABLETS || this == ORAL_TABLETS ? TABLET.name() : name();
    }
}
