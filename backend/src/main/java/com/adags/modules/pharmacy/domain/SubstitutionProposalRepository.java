package com.adags.modules.pharmacy.domain;

import java.util.Optional;
import java.util.UUID;

public interface SubstitutionProposalRepository {

    SubstitutionProposal insert(SubstitutionProposal proposal);

    Optional<SubstitutionProposal> findById(UUID id);

    /** Persists status/reviewedBy/reviewedAt/reviewNote/replacementLineId. */
    SubstitutionProposal update(SubstitutionProposal proposal);
}
