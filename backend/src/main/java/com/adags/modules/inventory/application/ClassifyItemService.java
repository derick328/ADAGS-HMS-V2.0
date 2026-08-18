package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemClassificationChange;
import com.adags.modules.inventory.domain.ItemClassificationChangeRepository;
import com.adags.modules.inventory.domain.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 12-pharmacist.md §10.6, 13-controlled-medicines-policy.md §2: assigning
// CONTROLLED itself is exempt from Item.assertDispensable() — that method
// exists to block *consuming* an already-controlled item, not to prevent
// classifying one as such in the first place. (Reclassifying an active
// item to CONTROLLED auto-deactivates it — see
// JdbcItemRepository.updateSchedule — so the invariant "active and
// CONTROLLED never coexist" still holds without this service needing to
// know about it.) Every change is logged permanently with a reason;
// CONTROLLED -> PRESCRIPTION_ONLY is not treated specially here beyond
// that — build brief item 4's "report" requirement is the detection report
// reading this same log, not an extra gate on the write.
@Service
public class ClassifyItemService {

    private final ItemRepository itemRepository;
    private final ItemClassificationChangeRepository changeRepository;

    public ClassifyItemService(ItemRepository itemRepository, ItemClassificationChangeRepository changeRepository) {
        this.itemRepository = itemRepository;
        this.changeRepository = changeRepository;
    }

    @Transactional
    public Item classify(ClassifyItemCommand command) {
        Item item = itemRepository.findById(command.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + command.itemId()));

        changeRepository.insert(ItemClassificationChange.record(item.id(), item.regulatorySchedule(),
                command.newSchedule(), command.reason(), command.actorUserId()));

        return itemRepository.updateSchedule(item.id(), command.newSchedule());
    }
}
