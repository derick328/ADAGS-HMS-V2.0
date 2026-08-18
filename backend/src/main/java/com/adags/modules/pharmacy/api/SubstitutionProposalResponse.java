package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.SubstitutionProposal;
import com.adags.modules.pharmacy.domain.SubstitutionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubstitutionProposalResponse(
        UUID id,
        UUID prescriptionLineId,
        UUID proposedItemId,
        String reason,
        SubstitutionStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        UUID replacementLineId
) {

    public static SubstitutionProposalResponse from(SubstitutionProposal proposal) {
        return new SubstitutionProposalResponse(proposal.id(), proposal.prescriptionLineId(), proposal.proposedItemId(),
                proposal.reason(), proposal.status(), proposal.reviewedBy(), proposal.reviewedAt(),
                proposal.reviewNote(), proposal.replacementLineId());
    }
}
