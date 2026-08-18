package com.adags.modules.pharmacy.api;

import com.adags.shared.kernel.AllergySummary;

// root CLAUDE.md "Safety": rendered inline and unmissable — every
// confirmation status, never filtered.
public record AllergyResponse(String description, String reaction, String severity, String confirmationStatus) {

    public static AllergyResponse from(AllergySummary summary) {
        return new AllergyResponse(summary.description(), summary.reaction(), summary.severity(),
                summary.confirmationStatus());
    }
}
