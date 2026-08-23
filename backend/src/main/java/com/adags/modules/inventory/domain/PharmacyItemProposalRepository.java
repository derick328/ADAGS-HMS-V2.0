package com.adags.modules.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PharmacyItemProposalRepository {

    PharmacyItemProposal insert(PharmacyItemProposal proposal);

    Optional<PharmacyItemProposal> findById(UUID id);

    /** Persists status/reviewedBy/reviewedAt/reviewNote/createdItemId. */
    PharmacyItemProposal update(PharmacyItemProposal proposal);

    /** The admin's review queue — 18-admin.md's "Pricing / price proposals". */
    List<PharmacyItemProposal> findPending();
}
