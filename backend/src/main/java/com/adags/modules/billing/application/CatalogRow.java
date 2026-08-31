package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;

// One validated row from the admin's .xlsx. classification is optional
// (~45% null in the source file, user spec); externalRefId is optional
// (kept for traceability, never a lookup key). priceMinor is nullable —
// a blank or negative PRICE cell is treated as "unpriced" so the admin
// can seed metadata now and set the real price on a later upload (root
// CLAUDE.md "Billing": a missing price never defaults to zero, so an
// unpriced catalogue item is visible but the billing gate refuses to
// charge for it until a positive price is entered). All whitelist
// violations are caught before a row becomes a CatalogRow.
//
// `code` is the *effective* code used for DB lookup and storage. On a
// clean file it equals `originalCode`; when the source file reuses the
// same PRODUCT CODE across multiple unrelated items (Kairuki.xlsx:
// DIS0001 covers 175 disposables), CatalogCodeResolver rewrites `code`
// on the second and later occurrences with a "#<externalRefId>" suffix
// so every row still has a unique storage code. `originalCode` always
// preserves the raw value as it appeared in the file so the admin UI
// and audit can trace back.
public record CatalogRow(
        int excelRowNumber,
        Integer externalRefId,
        String code,
        String originalCode,
        String name,
        String classification,
        CatalogItemType type,
        CatalogBillingCategory billingCategory,
        Long priceMinor
) {

    /** True when this row carries a real, chargeable price (>= 0 in minor units). */
    public boolean isPriced() {
        return priceMinor != null && priceMinor >= 0;
    }

    /** Returns a copy with the effective code rewritten — used by CatalogCodeResolver. */
    public CatalogRow withEffectiveCode(String effectiveCode) {
        return new CatalogRow(excelRowNumber, externalRefId, effectiveCode, originalCode,
                name, classification, type, billingCategory, priceMinor);
    }
}
