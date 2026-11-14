package com.adags.modules.inventory.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.modules.inventory.domain.PharmacyItemProposalRepository;
import com.adags.shared.kernel.Money;
import com.adags.shared.kernel.DosageForm;
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
        DosageForm form = DosageForm.requireSupported(command.proposedForm());
        if (command.proposedUnit() == null || command.proposedUnit().isBlank()
                || command.proposedUnit().trim().equalsIgnoreCase("UNIT")) {
            throw new IllegalArgumentException("Select a specific base inventory unit for this presentation.");
        }
        if (form.automaticQuantity() && (command.proposedUnit() == null
                || !form.name().equalsIgnoreCase(command.proposedUnit().trim()))) {
            throw new IllegalArgumentException("Solid-dose base unit must match the dosage form.");
        }
        PharmacyItemProposal proposal = PharmacyItemProposal.proposeNewItem(
                command.proposedName(), command.proposedClassification(), form.name(),
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

    @Transactional
    public PharmacyItemProposal proposeFormChange(java.util.UUID itemId, String form, String proposedUnit,
                                                   String justification, java.util.UUID proposedBy) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        DosageForm normalized = DosageForm.requireSupported(form);
        String targetUnit = proposedUnit == null || proposedUnit.isBlank() ? item.unit() : proposedUnit.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.name().equalsIgnoreCase(item.form()) && targetUnit.equalsIgnoreCase(item.unit())) {
            throw new IllegalArgumentException("The medicine already has this dosage form and base unit.");
        }
        String baseUnit = targetUnit.toUpperCase(java.util.Locale.ROOT);
        if (baseUnit.equals("UNIT")) {
            throw new IllegalArgumentException("Select a specific base inventory unit for this presentation.");
        }
        boolean solidBaseUnit = baseUnit.equals("TABLET") || baseUnit.equals("CAPSULE") || baseUnit.equals("CAPLET");
        if ((normalized.automaticQuantity() && !baseUnit.equals(normalized.name()))
                || (!normalized.automaticQuantity() && solidBaseUnit)) {
            throw new IllegalArgumentException("This correction conflicts with the product's base unit. Create a distinct catalogue product for the new presentation.");
        }
        return pharmacyItemProposalRepository.insert(PharmacyItemProposal.proposeFormChange(
                item.id(), normalized.name(), targetUnit, justification, proposedBy));
    }
}
