package com.adags.modules.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface PharmacyItemProposalRepository {

    PharmacyItemProposal insert(PharmacyItemProposal proposal);

    Optional<PharmacyItemProposal> findById(UUID id);

    /** Persists status/reviewedBy/reviewedAt/reviewNote/createdItemId. */
    PharmacyItemProposal update(PharmacyItemProposal proposal);
}
