package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.ProposalStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewPharmacyItemProposalRequest(@NotNull ProposalStatus decision, String reviewNote) {
}
