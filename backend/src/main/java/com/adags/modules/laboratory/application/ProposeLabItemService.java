package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.LabItemProposal;
import com.adags.modules.laboratory.domain.LabItemProposalParameter;
import com.adags.modules.laboratory.domain.LabItemProposalRepository;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 15-lab-technician.md §10: Lab Catalogue proposals — submit only, in this
// phase. Admin review/approval (LAB_ITEM_APPROVE) is a later, separate
// build against the same table (V62's comment).
@Service
public class ProposeLabItemService {

    private static final String CURRENCY_CODE = "TZS";

    private final LabItemProposalRepository labItemProposalRepository;

    public ProposeLabItemService(LabItemProposalRepository labItemProposalRepository) {
        this.labItemProposalRepository = labItemProposalRepository;
    }

    @Transactional
    public LabItemProposal propose(ProposeLabItemCommand command) {
        List<LabItemProposalParameter> parameters = command.parameters().stream()
                .map(p -> LabItemProposalParameter.create(p.name(), p.unit(), p.method(), 0))
                .toList();
        LabItemProposal proposal = LabItemProposal.propose(
                command.proposedName(), command.proposedClassification(),
                new Money(command.proposedPriceMinor(), CURRENCY_CODE), command.notes(), command.specimenType(),
                command.containerType(), command.expectedTatHours(), command.justification(), command.proposedBy(),
                parameters);
        return labItemProposalRepository.insert(proposal);
    }
}
