package com.adags.modules.referencedata.domain;

// root CLAUDE.md "Authorisation": "Second-person constraints at the
// database" — CHECK (proposed_by != reviewed_by) on icd10_synonym is the
// real guard (V13 migration); this is the friendly, pre-flight version of
// the same rule, thrown before the DB round trip rather than surfacing a
// raw constraint-violation message to the clinical lead.
public class SelfReviewNotAllowedException extends RuntimeException {

    public SelfReviewNotAllowedException() {
        super("A synonym cannot be approved or rejected by the same user who proposed it.");
    }
}
