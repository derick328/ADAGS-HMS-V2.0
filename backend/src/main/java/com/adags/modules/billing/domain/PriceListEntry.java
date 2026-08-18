package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 02-billing-architecture.md §4.2: "Prices are versioned, never edited in
// place. A change closes the current entry and opens a new one, so a
// March invoice always resolves March's price." validFrom/validTo record
// that; a null validTo is the current row (see the partial unique index
// in V15__service_item_and_price_list.sql).
public record PriceListEntry(
        UUID id,
        UUID priceListId,
        UUID serviceItemId,
        Money amount,
        LocalDate validFrom,
        LocalDate validTo,
        UUID createdBy,
        Instant createdAt
) {
}
