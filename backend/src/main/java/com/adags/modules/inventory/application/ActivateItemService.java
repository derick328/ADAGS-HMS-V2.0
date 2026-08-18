package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

// 14-controlled-medicines-tanzania.md §2/§5, 12-pharmacist.md §10.2:
// CONTROLLED is blocked from catalogue activation. item.assertDispensable()
// is the friendly-error half of the block; trg_block_controlled_item_activation
// (V24) is the database backstop.
@Service
public class ActivateItemService {

    private final ItemRepository itemRepository;

    public ActivateItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional
    public Item activate(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        item.assertDispensable();
        return itemRepository.activate(itemId);
    }
}
