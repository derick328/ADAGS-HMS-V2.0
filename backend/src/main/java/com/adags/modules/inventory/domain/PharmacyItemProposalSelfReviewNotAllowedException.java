package com.adags.modules.inventory.domain;

// Same second-person-constraint discipline as substitution_proposal and
// item_classification_change — the pharmacist who proposed a new item or a
// price change cannot also approve it. The friendly, application-layer
// half; the DB CHECK on pharmacy_item_proposal is the backstop.
public class PharmacyItemProposalSelfReviewNotAllowedException extends RuntimeException {

    public PharmacyItemProposalSelfReviewNotAllowedException() {
        super("A pharmacy item proposal cannot be reviewed by the same user who proposed it.");
    }
}
