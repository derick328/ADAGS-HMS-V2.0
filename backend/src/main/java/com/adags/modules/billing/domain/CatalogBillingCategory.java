package com.adags.modules.billing.domain;

// Excel's CATEGORY column vocabulary. Distinct from CatalogItemType — a
// finer billing-line grouping used for invoice/statement rollups. Values
// mirror the CHECK constraint on service_item.billing_category (V67).
public enum CatalogBillingCategory {
    SURGICAL_CHARGES,
    MEDICINES_AND_CONSUMABLES,
    DIAGNOSTIC_EXAMINATIONS,
    PROCEDURAL_CHARGES,
    INPATIENT_CHARGES,
    REGISTRATION_CONSULTATION_CHARGES,
    OTHER_CHARGES,
    ADVANCE_PAYMENT
}
