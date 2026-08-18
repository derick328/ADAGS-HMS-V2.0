package com.adags.modules.inventory.domain;

import java.util.UUID;

public class PharmacyItemProposalAlreadyReviewedException extends RuntimeException {

    public PharmacyItemProposalAlreadyReviewedException(UUID proposalId) {
        super("Pharmacy item proposal " + proposalId + " has already been reviewed.");
    }
}
