package com.adags.modules.billing.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PriceListEntryRepository {

    /** The current (validTo IS NULL) row for this item under this payer category — the join to price_list is internal. */
    Optional<PriceListEntry> findCurrent(UUID serviceItemId, PayerCategory payerCategory);

    PriceListEntry insert(PriceListEntry entry);

    /** 02-billing-architecture.md §4.2: "A change closes the current entry and opens a new one." Never an edit of amount_minor in place. */
    void close(UUID entryId, LocalDate validTo);
}
