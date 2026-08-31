package com.adags.modules.billing.api;

import com.adags.modules.billing.application.CatalogImportPreview;

import java.util.List;
import java.util.UUID;

public record CatalogImportPreviewResponse(
        UUID batchId,
        int rowCount,
        int newItemCount,
        int priceChangeCount,
        int metadataChangeCount,
        int unchangedCount,
        int duplicateCodeCount,
        List<NewItemDto> newItems,
        List<PriceChangeDto> priceChanges,
        List<MetadataChangeDto> metadataChanges,
        List<DuplicateCodeDto> duplicateCodes
) {

    // priceMinor is nullable — a new catalogue item can arrive unpriced,
    // deliberately (see CatalogRow's doc-comment). `code` is the
    // effective code (may carry a "#<externalRefId>" suffix on rows whose
    // raw PRODUCT CODE collides with another in the file); `originalCode`
    // is what the file literally said.
    public record NewItemDto(int excelRow, String code, String originalCode, String name, Long priceMinor) {
    }

    public record PriceChangeDto(int excelRow, String code, String originalCode, String name, long previousMinor, long newMinor) {
    }

    public record MetadataChangeDto(int excelRow, String code, String originalCode, String name, List<String> changedFields) {
    }

    public record DuplicateCodeDto(String originalCode, List<DuplicateOccurrenceDto> occurrences) {
    }

    public record DuplicateOccurrenceDto(int excelRow, String effectiveCode, String name) {
    }

    public static CatalogImportPreviewResponse from(CatalogImportPreview p) {
        return new CatalogImportPreviewResponse(
                p.batchId(),
                p.rowCount(),
                p.newItems().size(),
                p.priceChanges().size(),
                p.metadataChanges().size(),
                p.unchangedCount(),
                p.duplicateCodes().size(),
                p.newItems().stream()
                        .map(n -> new NewItemDto(n.excelRow(), n.code(), n.originalCode(), n.name(), n.priceMinor())).toList(),
                p.priceChanges().stream()
                        .map(c -> new PriceChangeDto(c.excelRow(), c.code(), c.originalCode(), c.name(), c.previousMinor(), c.newMinor())).toList(),
                p.metadataChanges().stream()
                        .map(m -> new MetadataChangeDto(m.excelRow(), m.code(), m.originalCode(), m.name(), m.changedFields())).toList(),
                p.duplicateCodes().stream()
                        .map(d -> new DuplicateCodeDto(
                                d.originalCode(),
                                d.occurrences().stream()
                                        .map(o -> new DuplicateOccurrenceDto(o.excelRow(), o.effectiveCode(), o.name()))
                                        .toList()))
                        .toList());
    }
}
