package com.adags.modules.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository {

    Item insert(Item item);

    Optional<Item> findById(UUID id);

    Optional<Item> findByCode(String code);

    /** Sets active = true. The CONTROLLED block is enforced both by the caller's Item.assertDispensable() and by V24's activation trigger. */
    Item activate(UUID itemId);

    Item updateSchedule(UUID itemId, RegulatorySchedule newSchedule);

    /** Reviewed product-presentation correction; previous prescriptions retain their form snapshot. */
    Item updateFormAndUnit(UUID itemId, String form, String unit);

    /** 17-ward-nurse.md §5.3: whether administering this item requires a witness. */
    Item updateAdministrationWitness(UUID itemId, boolean requiresWitness);

    /**
     * Name search, case-insensitive substring — the prescriber typeahead
     * (13-controlled-medicines-policy.md §4). Returns every match
     * regardless of regulatory_schedule or active state: a CONTROLLED item
     * must appear as visible-and-unselectable, never silently absent.
     */
    List<Item> searchByName(String query, int limit);
}
