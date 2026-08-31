package com.adags.modules.billing.domain;

// Excel's TYPE column vocabulary (phase 5/9 catalogue import). Kept in
// sync with the CHECK constraint on service_item.type (V67). A value
// outside this enum can never reach the DB — the parser rejects the row.
public enum CatalogItemType {
    PHARMACY,
    PROCEDURE,
    SURGERY,
    LABORATORY,
    RADIOLOGY,
    OPTICAL,
    DIALYSIS,
    OTHERS,
    OTHER_GENERAL_CHARGES
}
