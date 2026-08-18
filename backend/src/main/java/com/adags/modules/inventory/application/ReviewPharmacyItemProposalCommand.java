package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.ProposalStatus;

import java.util.UUID;

public record ReviewPharmacyItemProposalCommand(UUID proposalId, ProposalStatus decision, UUID reviewedBy, String reviewNote) {
}
