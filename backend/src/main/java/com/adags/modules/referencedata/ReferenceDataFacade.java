package com.adags.modules.referencedata;

import java.util.Optional;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Returns a plain String, never Icd10Code — a diagnosis-time
// caller (clinical module) needs exactly the title to snapshot, nothing
// else, matching PatientFacade's precedent of not leaking its own domain
// types across the boundary.
public interface ReferenceDataFacade {

    /**
     * The current title for an active, billable ICD-10 code — empty if the
     * code doesn't exist, is retired, or is a non-billable category header
     * (10-clinical-reference-data.md §2.1 / 07-doctor.md §4.1: only a
     * billable code is a valid diagnosis).
     */
    Optional<String> resolveBillableIcd10Title(String code);

    /** Active diagnosis-selectable title, including WHO rows pending billing review. */
    Optional<String> resolveSelectableIcd10Title(String code);
}
