package com.adags.modules.inventory.domain;

import java.util.List;

public interface ItemClassificationChangeRepository {

    // Append-only — no update/delete method exists on this interface by
    // design (root CLAUDE.md "Data"; build brief item 4: "permanently").
    ItemClassificationChange insert(ItemClassificationChange change);

    /** Every CONTROLLED -> PRESCRIPTION_ONLY reclassification, newest first — build brief item 4. */
    List<ItemClassificationChange> findControlledReleases();
}
