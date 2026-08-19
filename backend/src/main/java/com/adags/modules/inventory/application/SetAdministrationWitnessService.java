package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 17-ward-nurse.md §5.3: "Standard practice for a defined class — IV drugs,
// insulin, anticoagulants, paediatric doses, and anything the clinical lead
// adds." Same catalogue-classification authority as ClassifyItemService
// (ITEM_CLASSIFY), not a separate ward permission — this is a fact about
// the drug, not about the ward.
@Service
public class SetAdministrationWitnessService {

    private final ItemRepository itemRepository;

    public SetAdministrationWitnessService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional
    public Item setRequiresWitness(SetAdministrationWitnessCommand command) {
        itemRepository.findById(command.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + command.itemId()));
        return itemRepository.updateAdministrationWitness(command.itemId(), command.requiresWitness());
    }
}
