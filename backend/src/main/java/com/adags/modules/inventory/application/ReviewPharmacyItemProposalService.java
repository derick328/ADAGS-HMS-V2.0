package com.adags.modules.inventory.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.modules.inventory.domain.PharmacyItemProposalAlreadyReviewedException;
import com.adags.modules.inventory.domain.PharmacyItemProposalRepository;
import com.adags.modules.inventory.domain.PharmacyItemProposalSelfReviewNotAllowedException;
import com.adags.modules.inventory.domain.ProposalStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §9.2: approving a NEW_ITEM proposal creates the item —
// always UNCLASSIFIED and inactive, exactly like CreateItemService's
// direct-create path (see V30's comment for why regulatory_schedule is
// never taken from the proposal) — and sets its first price. Approving a
// PRICE_CHANGE re-prices the existing item. Both post through
// BillingFacade.setPharmacyItemPrice, the same versioned-pricing path
// (close the old entry, open a new one).
@Service
public class ReviewPharmacyItemProposalService {

    private final PharmacyItemProposalRepository pharmacyItemProposalRepository;
    private final ItemRepository itemRepository;
    private final BillingFacade billingFacade;

    public ReviewPharmacyItemProposalService(
            PharmacyItemProposalRepository pharmacyItemProposalRepository,
            ItemRepository itemRepository,
            BillingFacade billingFacade
    ) {
        this.pharmacyItemProposalRepository = pharmacyItemProposalRepository;
        this.itemRepository = itemRepository;
        this.billingFacade = billingFacade;
    }

    @Transactional
    public PharmacyItemProposal review(ReviewPharmacyItemProposalCommand command) {
        if (command.decision() != ProposalStatus.APPROVED && command.decision() != ProposalStatus.REJECTED) {
            throw new IllegalArgumentException("A review decision must be APPROVED or REJECTED, not " + command.decision() + ".");
        }
        PharmacyItemProposal proposal = pharmacyItemProposalRepository.findById(command.proposalId())
                .orElseThrow(() -> new NoSuchElementException("Pharmacy item proposal not found: " + command.proposalId()));
        if (proposal.status() != ProposalStatus.PROPOSED) {
            throw new PharmacyItemProposalAlreadyReviewedException(proposal.id());
        }
        if (proposal.proposedBy().equals(command.reviewedBy())) {
            throw new PharmacyItemProposalSelfReviewNotAllowedException();
        }

        if (command.decision() == ProposalStatus.REJECTED) {
            return pharmacyItemProposalRepository.update(withReview(proposal, ProposalStatus.REJECTED, command, null));
        }

        UUID createdItemId = switch (proposal.proposalType()) {
            case NEW_ITEM -> approveNewItem(proposal, command.reviewedBy());
            case PRICE_CHANGE -> {
                approvePriceChange(proposal, command.reviewedBy());
                yield null;
            }
            case FORM_CHANGE -> {
                itemRepository.updateFormAndUnit(proposal.itemId(), proposal.proposedForm(), proposal.proposedUnit());
                yield null;
            }
        };

        return pharmacyItemProposalRepository.update(withReview(proposal, ProposalStatus.APPROVED, command, createdItemId));
    }

    private UUID approveNewItem(PharmacyItemProposal proposal, UUID reviewedBy) {
        // Human-legible and guaranteed unique (derived from the proposal's
        // own id) — §9.2 says "auto-generated," not sequential/gapless
        // like an invoice or hospital number, so no shared numbering
        // scheme is needed here.
        String code = "ITEM-" + proposal.id().toString().substring(0, 8).toUpperCase();
        Item item = itemRepository.insert(Item.create(code, proposal.proposedName(), proposal.proposedClassification(),
                proposal.proposedForm(), null, proposal.proposedUnit(), 0, reviewedBy));
        billingFacade.setPharmacyItemPrice(item.code(), item.name(), proposal.proposedPrice().amountMinor(), reviewedBy);
        return item.id();
    }

    private void approvePriceChange(PharmacyItemProposal proposal, UUID reviewedBy) {
        Item item = itemRepository.findById(proposal.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + proposal.itemId()));
        billingFacade.setPharmacyItemPrice(item.code(), item.name(), proposal.proposedPrice().amountMinor(), reviewedBy);
    }

    private PharmacyItemProposal withReview(PharmacyItemProposal proposal, ProposalStatus status,
                                             ReviewPharmacyItemProposalCommand command, UUID createdItemId) {
        return new PharmacyItemProposal(proposal.id(), proposal.proposalType(), proposal.itemId(),
                proposal.proposedName(), proposal.proposedClassification(), proposal.proposedForm(),
                proposal.proposedUnit(), proposal.proposedPrice(), proposal.currentPrice(), proposal.justification(),
                proposal.proposedBy(), proposal.proposedAt(), status, command.reviewedBy(), Instant.now(),
                command.reviewNote(), createdItemId);
    }
}
