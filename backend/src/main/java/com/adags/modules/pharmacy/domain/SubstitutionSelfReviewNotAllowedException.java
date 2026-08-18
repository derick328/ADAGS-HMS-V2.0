package com.adags.modules.pharmacy.domain;

// Same second-person-constraint discipline as referencedata's
// SelfReviewNotAllowedException and V24's item_classification_change — the
// friendly, application-layer half; the DB CHECK on substitution_proposal
// is the backstop for a path that skips this call.
public class SubstitutionSelfReviewNotAllowedException extends RuntimeException {

    public SubstitutionSelfReviewNotAllowedException() {
        super("A substitution proposal cannot be reviewed by the same user who proposed it.");
    }
}
