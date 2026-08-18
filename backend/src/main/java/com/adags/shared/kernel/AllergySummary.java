package com.adags.shared.kernel;

// What ClinicalFacade hands another module (pharmacy, at dispensing) for
// inline allergy display — root CLAUDE.md "Safety": "Allergy records
// render inline and unmissable... never collapsed, never behind a tab."
// confirmationStatus is the raw AllergyConfirmationStatus enum name
// (UNCONFIRMED/CONFIRMED/REFUTED) — the pharmacist screen shows every one
// of them identically, per 12-pharmacist.md §5.2, so this is a display
// value, not a filter key. Never AllergyRecord (clinical's own domain
// type) across the module boundary.
public record AllergySummary(String description, String reaction, String severity, String confirmationStatus) {
}
