package com.adags.modules.pharmacy.domain;

import java.util.UUID;

public class SubstitutionAlreadyReviewedException extends RuntimeException {

    public SubstitutionAlreadyReviewedException(UUID proposalId) {
        super("Substitution proposal " + proposalId + " has already been reviewed.");
    }
}
