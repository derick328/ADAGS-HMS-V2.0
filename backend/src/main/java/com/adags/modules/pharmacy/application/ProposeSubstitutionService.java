package com.adags.modules.pharmacy.application;

import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.pharmacy.domain.ItemNotDispensableException;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.SubstitutionNotAllowedException;
import com.adags.modules.pharmacy.domain.SubstitutionProposal;
import com.adags.modules.pharmacy.domain.SubstitutionProposalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 12-pharmacist.md §5.1: "The prescriber's substitution_allowed flag
// governs whether a proposal is possible at all."
@Service
public class ProposeSubstitutionService {

    private final PrescriptionLineRepository prescriptionLineRepository;
    private final SubstitutionProposalRepository substitutionProposalRepository;
    private final InventoryFacade inventoryFacade;

    public ProposeSubstitutionService(
            PrescriptionLineRepository prescriptionLineRepository,
            SubstitutionProposalRepository substitutionProposalRepository,
            InventoryFacade inventoryFacade
    ) {
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.substitutionProposalRepository = substitutionProposalRepository;
        this.inventoryFacade = inventoryFacade;
    }

    @Transactional
    public SubstitutionProposal propose(ProposeSubstitutionCommand command) {
        PrescriptionLine line = prescriptionLineRepository.findById(command.prescriptionLineId())
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + command.prescriptionLineId()));
        if (!line.substitutionAllowed()) {
            throw new SubstitutionNotAllowedException(line.id(), "the prescriber did not allow substitution on this line");
        }
        if (!inventoryFacade.isDispensable(command.proposedItemId())) {
            throw new ItemNotDispensableException(command.proposedItemId());
        }

        SubstitutionProposal proposal = SubstitutionProposal.propose(
                line.id(), command.proposedItemId(), command.reason(), command.proposedBy());
        return substitutionProposalRepository.insert(proposal);
    }
}
