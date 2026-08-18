package com.adags.modules.billing.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// V19__provider_billing_category.sql: the role_code -> service_item_code
// mapping BillingFacadeImpl previously hardcoded as DOCTOR_ROLE/
// SPECIALIST_DOCTOR_ROLE constants. Same current-row versioning as
// PriceListEntry — a hospital adding a registrar rate inserts a row rather
// than needing a redeploy.
public record ProviderBillingCategory(
        UUID id,
        String roleCode,
        String serviceItemCode,
        int priority,
        LocalDate validFrom,
        LocalDate validTo,
        UUID createdBy,
        Instant createdAt
) {
}
