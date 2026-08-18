package com.adags.modules.billing.domain;

import java.util.UUID;

// 02-billing-architecture.md §11: "the chargeable thing." No facility_id —
// root CLAUDE.md "Facility": this is a definition ("what a consultation
// is"), not an occurrence at a place, same reasoning as Icd10Code.
public record ServiceItem(UUID id, String code, String name, ServiceItemCategory category, boolean isActive) {
}
