package com.adags.modules.billing.application;

import java.util.List;
import java.util.UUID;

// Result of a preview call. The admin sees these counts + the itemized
// change lists on the pricing page; only after they hit Apply does
// ApplyCatalogImportService write to service_item / price_list_entry.
public record CatalogImportPreview(
        UUID batchId,
        int rowCount,
        List<NewItem> newItems,
        List<PriceChange> priceChanges,
        List<MetadataChange> metadataChanges,
        int unchangedCount,
        // Groups of rows whose PRODUCT CODE was reused inside the file.
        // The importer disambiguates by suffixing the second and later
        // occurrences (see CatalogCodeResolver); this list is
        // informational so the admin can see what was rewritten before
        // hitting Apply. Empty on a clean file.
        List<DuplicateCode> duplicateCodes
) {

    // priceMinor is nullable — a new catalogue item can arrive with no
    // price so the metadata seeds now and pricing lands on a later
    // upload. Root CLAUDE.md "Billing": the missing price is a real
    // signal, not a zero.
    //
    // `code` is the effective (possibly suffixed) code that will land in
    // service_item.code; `originalCode` is the raw value from the file.
    public record NewItem(int excelRow, String code, String originalCode, String name, Long priceMinor) {
    }

    public record PriceChange(int excelRow, String code, String originalCode, String name, long previousMinor, long newMinor) {
    }

    public record MetadataChange(int excelRow, String code, String originalCode, String name, List<String> changedFields) {
    }

    public record DuplicateCode(String originalCode, List<DuplicateOccurrence> occurrences) {
    }

    public record DuplicateOccurrence(int excelRow, String effectiveCode, String name) {
    }
}
