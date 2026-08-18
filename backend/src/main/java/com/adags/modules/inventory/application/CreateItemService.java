package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.DuplicateItemCodeException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 12-pharmacist.md §9.1/§9.2: full catalogue-with-approval (NEW_ITEM
// proposal, product code auto-generation, category/type forced to
// MEDICINES AND CONSUMABLES/PHARMACY) is explicitly deferred — this is the
// direct-create path the pharmacy build brief scopes for now, gated by
// ITEM_CLASSIFY at the controller since the same authority (pharmacist-in-
// charge / CLINICAL_LEAD) that will classify the item is the one creating
// it. A new item is always UNCLASSIFIED and inactive (Item.create) — it
// becomes dispensable only through the classify/activate actions in build
// brief item 2.
@Service
public class CreateItemService {

    private final ItemRepository itemRepository;

    public CreateItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional
    public Item create(CreateItemCommand command) {
        if (itemRepository.findByCode(command.code()).isPresent()) {
            throw new DuplicateItemCodeException(command.code());
        }
        Item item = Item.create(command.code(), command.name(), command.classification(), command.form(),
                command.strength(), command.unit(), command.reorderLevel(), command.actorUserId());
        return itemRepository.insert(item);
    }
}
