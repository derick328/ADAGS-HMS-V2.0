package com.adags.modules.inventory.domain;

// 14-controlled-medicines-tanzania.md §2, 12-pharmacist.md §10.1/§10.2.
// CONTROLLED is blocked outright everywhere a controlled item could move.
// UNCLASSIFIED is D9's confirmed exception to 13-*.md's original fail-closed
// default: dispensable like any other non-controlled schedule, never
// blocked pending review. Enforcement of the CONTROLLED block itself lives
// in the repository layer (build brief item 2), not here — this is only
// the classification the block condition reads.
public enum RegulatorySchedule {
    CONTROLLED,
    PRESCRIPTION_ONLY,
    PHARMACY_ONLY_BTC,
    PHARMACY_ONLY_OTC,
    GENERAL_SALES,
    ADDO_PRESCRIPTION,
    RADIOPHARMACEUTICAL,
    UNCLASSIFIED;

    public boolean isDispensable() {
        return this != CONTROLLED;
    }
}
