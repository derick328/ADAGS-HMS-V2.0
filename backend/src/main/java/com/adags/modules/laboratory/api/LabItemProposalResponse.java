package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.LabItemProposal;
import com.adags.modules.laboratory.domain.LabItemProposalStatus;

import java.util.List;
import java.util.UUID;

public record LabItemProposalResponse(
        UUID id,
        String proposedName,
        String proposedClassification,
        long proposedPriceMinor,
        String currencyCode,
        String notes,
        String specimenType,
        String containerType,
        int expectedTatHours,
        String justification,
        LabItemProposalStatus status,
        List<ProposedParameterResponse> parameters
) {
    public static LabItemProposalResponse from(LabItemProposal proposal) {
        return new LabItemProposalResponse(
                proposal.id(), proposal.proposedName(), proposal.proposedClassification(),
                proposal.proposedPrice().amountMinor(), proposal.proposedPrice().currencyCode(), proposal.notes(),
                proposal.specimenType(), proposal.containerType(), proposal.expectedTatHours(),
                proposal.justification(), proposal.status(),
                proposal.parameters().stream()
                        .map(p -> new ProposedParameterResponse(p.name(), p.unit(), p.method()))
                        .toList());
    }

    public record ProposedParameterResponse(String name, String unit, String method) {
    }
}
