package com.adags.modules.inventory.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.modules.inventory.domain.PharmacyItemProposalRepository;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 12-pharmacist.md §9.2: new items and price changes, both through one
// approval queue.
@Service
public class ProposePharmacyItemService {

    private static final String CURRENCY_CODE = "TZS";

    private final PharmacyItemProposalRepository pharmacyItemProposalRepository;
    private final ItemRepository itemRepository;
    private final BillingFacade billingFacade;

    public ProposePharmacyItemService(
            PharmacyItemProposalRepository pharmacyItemProposalRepository,
            ItemRepository itemRepository,
            BillingFacade billingFacade
    ) {
        this.pharmacyItemProposalRepository = pharmacyItemProposalRepository;
        this.itemRepository = itemRepository;
        this.billingFacade = billingFacade;
    }

    @Transactional
    public PharmacyItemProposal proposeNewItem(ProposeNewItemCommand command) {
        PharmacyItemProposal proposal = PharmacyItemProposal.proposeNewItem(
                command.proposedName(), command.proposedClassification(), command.proposedForm(),
                command.proposedUnit(), new Money(command.proposedPriceMinor(), CURRENCY_CODE),
                command.justification(), command.proposedBy());
        return pharmacyItemProposalRepository.insert(proposal);
    }

    @Transactional
    public PharmacyItemProposal proposePriceChange(ProposePriceChangeCommand command) {
        Item item = itemRepository.findById(command.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + command.itemId()));
        Money currentPrice = billingFacade.findCurrentPharmacyItemPrice(item.code()).orElse(null);

        PharmacyItemProposal proposal = PharmacyItemProposal.proposePriceChange(item.id(),
                new Money(command.proposedPriceMinor(), CURRENCY_CODE), currentPrice, command.justification(),
                command.proposedBy());
        return pharmacyItemProposalRepository.insert(proposal);
    }
}
