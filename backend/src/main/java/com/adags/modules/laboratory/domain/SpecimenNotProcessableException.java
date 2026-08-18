package com.adags.modules.laboratory.domain;

import java.util.UUID;

// A result can only be entered against a specimen accepted with ADEQUATE
// quality (SpecimenStatus.IN_PROGRESS) — 15-lab-technician.md §4.3: a
// REJECTED specimen has nothing to process, and a still-COLLECTED one
// hasn't had quality assessed yet.
public class SpecimenNotProcessableException extends RuntimeException {

    public SpecimenNotProcessableException(UUID specimenId, SpecimenStatus status) {
        super("Specimen " + specimenId + " is " + status + ", not IN_PROGRESS — no result can be entered against it.");
    }
}
