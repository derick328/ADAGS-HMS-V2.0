package com.adags.modules.inventory.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §9.2: new items AND price changes both through one
// approval queue — "a pharmacist creating an item at an arbitrary price
// bypasses price control from the other direction." justification is
// required for PRICE_CHANGE only ("a price movement with no stated reason
// gives the approving admin nothing to approve on"); NEW_ITEM has no such
// requirement in the build brief.
public record PharmacyItemProposal(
        UUID id,
        ProposalType proposalType,
        UUID itemId,
        String proposedName,
        String proposedClassification,
        String proposedForm,
        String proposedUnit,
        Money proposedPrice,
        Money currentPrice,
        String justification,
        UUID proposedBy,
        Instant proposedAt,
        ProposalStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        UUID createdItemId
) {

    public static PharmacyItemProposal proposeNewItem(String proposedName, String proposedClassification,
                                                        String proposedForm, String proposedUnit, Money proposedPrice,
                                                        String justification, UUID proposedBy) {
        requireField(proposedName, "proposedName");
        requireField(proposedClassification, "proposedClassification");
        requireField(proposedForm, "proposedForm");
        requireField(proposedUnit, "proposedUnit");
        return new PharmacyItemProposal(null, ProposalType.NEW_ITEM, null, proposedName, proposedClassification,
                proposedForm, proposedUnit, proposedPrice, null, justification, proposedBy, null,
                ProposalStatus.PROPOSED, null, null, null, null);
    }

    public static PharmacyItemProposal proposePriceChange(UUID itemId, Money proposedPrice, Money currentPrice,
                                                            String justification, UUID proposedBy) {
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("justification is required for a price change.");
        }
        return new PharmacyItemProposal(null, ProposalType.PRICE_CHANGE, itemId, null, null, null, null,
                proposedPrice, currentPrice, justification, proposedBy, null, ProposalStatus.PROPOSED, null, null,
                null, null);
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
    }
}
