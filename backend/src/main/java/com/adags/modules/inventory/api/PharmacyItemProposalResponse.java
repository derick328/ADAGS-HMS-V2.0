package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.modules.inventory.domain.ProposalStatus;
import com.adags.modules.inventory.domain.ProposalType;

import java.util.UUID;

public record PharmacyItemProposalResponse(
        UUID id,
        ProposalType proposalType,
        UUID itemId,
        String proposedName,
        long proposedPriceAmountMinor,
        Long currentPriceAmountMinor,
        String justification,
        ProposalStatus status,
        String reviewNote,
        UUID createdItemId
) {

    public static PharmacyItemProposalResponse from(PharmacyItemProposal proposal) {
        return new PharmacyItemProposalResponse(proposal.id(), proposal.proposalType(), proposal.itemId(),
                proposal.proposedName(), proposal.proposedPrice().amountMinor(),
                proposal.currentPrice() == null ? null : proposal.currentPrice().amountMinor(),
                proposal.justification(), proposal.status(), proposal.reviewNote(), proposal.createdItemId());
    }
}
