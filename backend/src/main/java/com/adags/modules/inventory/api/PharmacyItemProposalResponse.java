package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.modules.inventory.domain.ProposalStatus;
import com.adags.modules.inventory.domain.ProposalType;

import java.time.Instant;
import java.util.UUID;

public record PharmacyItemProposalResponse(
        UUID id,
        ProposalType proposalType,
        UUID itemId,
        String proposedName,
        String proposedClassification,
        String proposedForm,
        String proposedUnit,
        long proposedPriceAmountMinor,
        Long currentPriceAmountMinor,
        String currencyCode,
        String justification,
        UUID proposedBy,
        Instant proposedAt,
        ProposalStatus status,
        String reviewNote,
        UUID createdItemId
) {

    public static PharmacyItemProposalResponse from(PharmacyItemProposal proposal) {
        return new PharmacyItemProposalResponse(proposal.id(), proposal.proposalType(), proposal.itemId(),
                proposal.proposedName(), proposal.proposedClassification(), proposal.proposedForm(), proposal.proposedUnit(),
                proposal.proposedPrice().amountMinor(),
                proposal.currentPrice() == null ? null : proposal.currentPrice().amountMinor(),
                proposal.proposedPrice().currencyCode(),
                proposal.justification(), proposal.proposedBy(), proposal.proposedAt(),
                proposal.status(), proposal.reviewNote(), proposal.createdItemId());
    }
}
