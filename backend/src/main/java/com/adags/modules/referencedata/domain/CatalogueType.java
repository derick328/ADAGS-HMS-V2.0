package com.adags.modules.referencedata.domain;

// 10-clinical-reference-data.md §2.4/§6: "is_common, department scoping,
// and synonyms apply equally to lab tests, drugs, and procedures... this is
// what makes the component genuinely one thing with four configurations."
// Only ICD10 is built (item 4 of this phase); SERVICE_ITEM and ITEM are
// modelled now so ReferenceSearchLog.catalogue_type never needs a later
// rename — the same reasoning as EncounterState modelling its full
// transition graph before every caller exists.
public enum CatalogueType {
    ICD10,
    SERVICE_ITEM,
    ITEM
}
