package com.adags.modules.surgery.domain;

import java.util.UUID;

// root CLAUDE.md task text: "Consent blocks SCHEDULING, not billing. No
// signed document, no theatre slot." A checkbox alone is not consent — the
// document key is the record, so both consent_obtained and a non-blank
// consent_document_key are required before a SurgeryOrder can be created.
public class ConsentNotObtainedException extends RuntimeException {

    private final UUID encounterId;

    public ConsentNotObtainedException(UUID encounterId) {
        super("Surgery cannot be scheduled without a signed consent document: encounter " + encounterId);
        this.encounterId = encounterId;
    }

    public UUID encounterId() {
        return encounterId;
    }
}
