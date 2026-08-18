package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.ItemClassificationChange;
import com.adags.modules.inventory.domain.MisclassificationCandidate;

import java.util.List;

// 12-pharmacist.md §10.6: the two halves of the safety net now that
// UNCLASSIFIED is dispensable (D9) — items that look like they might be
// misclassified, and the permanent record of every time a controlled item
// was actually released from that status (build brief item 4).
public record MisclassificationReport(
        List<MisclassificationCandidate> candidates,
        List<ItemClassificationChange> controlledReleases
) {
}
