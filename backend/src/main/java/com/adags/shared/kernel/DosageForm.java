package com.adags.shared.kernel;

import java.util.Locale;

/** Supported medicine presentations and their supply-quantity policy. */
public enum DosageForm {
    CAPSULE, TABLET, CAPLET, SYRUP, INJECTION, CREAM, GEL, LOTION, PASTE;

    public static DosageForm fromCatalogue(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("ORAL SUSPENSION") || normalized.equals("SUSPENSION")) return SYRUP;
        if (normalized.endsWith("S")) {
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
        return this == CAPSULE || this == TABLET || this == CAPLET;
    }
}
