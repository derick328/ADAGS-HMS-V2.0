package com.adags.modules.billing.domain;

import java.util.UUID;

// One row per PayerCategory (V15 migration seeds all five) — the
// per-payer container PriceListEntry rows are versioned within.
public record PriceList(UUID id, PayerCategory payerCategory, String name, boolean isActive) {
}
