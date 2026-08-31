package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogImportBatch;
import com.adags.modules.billing.domain.CatalogImportBatchRepository;
import com.adags.modules.billing.domain.CatalogImportStatus;
import com.adags.modules.billing.domain.DuplicateCatalogFileException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Phase 5/9 catalogue import — stage 1 (preview). Parses the .xlsx,
// validates every row, computes the diff against current state, archives
// the file to MinIO, records a PREVIEWED catalog_import_batch row.
// Nothing in service_item or price_list_entry is written here — that
// happens in ApplyCatalogImportService when the admin confirms.
//
// Duplicate PRODUCT CODE handling: the parser accepts the raw file as
// given; CatalogCodeResolver rewrites the second and later occurrences
// of any repeated code to a "#<externalRefId>" effective code (see
// CatalogRow doc). The DuplicateCode list on the preview surfaces every
// rewrite so the admin can see what was disambiguated. Kairuki.xlsx
// reuses codes like DIS0001 across 175 unrelated items — treating that
// as a hard rejection would refuse the whole import.
//
// Idempotency: a byte-identical re-upload of a file we've already
// PREVIEWED (or APPLIED) returns the existing batch — the partial unique
// index idx_catalog_import_batch_sha256_active is the ultimate guard,
// this check surfaces it as a typed exception instead of a raw DB error.
@Service
public class PreviewCatalogImportService {

    private final CatalogRowParserPort parser;
    private final CatalogFileStoragePort storage;
    private final CatalogItemLookupPort itemLookup;
    private final CatalogImportBatchRepository batchRepository;
    private final Clock clock;

    public PreviewCatalogImportService(
            CatalogRowParserPort parser,
            CatalogFileStoragePort storage,
            CatalogItemLookupPort itemLookup,
            CatalogImportBatchRepository batchRepository,
            Clock clock
    ) {
        this.parser = parser;
        this.storage = storage;
        this.itemLookup = itemLookup;
        this.batchRepository = batchRepository;
        this.clock = clock;
    }

    @Transactional
    public CatalogImportPreview preview(byte[] fileBytes, String originalFilename, UUID actorUserId) {
        String sha256 = sha256Hex(fileBytes);
        batchRepository.findActiveByFileSha256(sha256).ifPresent(existing -> {
            throw new DuplicateCatalogFileException(existing.id(), existing.status().name());
        });

        List<CatalogRow> rawRows = parser.parse(new ByteArrayInputStream(fileBytes));
        CatalogCodeResolver.Resolution resolution = CatalogCodeResolver.resolve(rawRows);
        List<CatalogRow> rows = resolution.resolvedRows();

        Map<String, CatalogItemView> existing = existingByCode(rows);
        List<CatalogImportPreview.NewItem> newItems = new ArrayList<>();
        List<CatalogImportPreview.PriceChange> priceChanges = new ArrayList<>();
        List<CatalogImportPreview.MetadataChange> metadataChanges = new ArrayList<>();
        int unchanged = 0;

        for (CatalogRow row : rows) {
            CatalogItemView current = existing.get(row.code());
            if (current == null) {
                newItems.add(new CatalogImportPreview.NewItem(
                        row.excelRowNumber(), row.code(), row.originalCode(), row.name(),
                        row.isPriced() ? row.priceMinor() : null));
                continue;
            }
            List<String> changedFields = diffMetadata(row, current);
            // Unpriced incoming rows leave the existing current price
            // alone — the admin's intent is "fix later", not "clear".
            boolean priceChanged = row.isPriced()
                    && (current.currentCashPriceMinor() == null
                        || current.currentCashPriceMinor() != row.priceMinor());
            if (priceChanged) {
                priceChanges.add(new CatalogImportPreview.PriceChange(
                        row.excelRowNumber(), row.code(), row.originalCode(), row.name(),
                        current.currentCashPriceMinor() == null ? 0L : current.currentCashPriceMinor(),
                        row.priceMinor()));
            }
            if (!changedFields.isEmpty()) {
                metadataChanges.add(new CatalogImportPreview.MetadataChange(
                        row.excelRowNumber(), row.code(), row.originalCode(), row.name(), changedFields));
            }
            if (!priceChanged && changedFields.isEmpty()) {
                unchanged++;
            }
        }

        List<CatalogImportPreview.DuplicateCode> duplicateCodes = resolution.duplicates().stream()
                .map(d -> new CatalogImportPreview.DuplicateCode(
                        d.originalCode(),
                        d.occurrences().stream()
                                .map(o -> new CatalogImportPreview.DuplicateOccurrence(o.excelRow(), o.effectiveCode(), o.name()))
                                .toList()))
                .toList();

        UUID batchId = UUID.randomUUID();
        String storageKey = storage.store(batchId, originalFilename, fileBytes);
        Instant now = clock.instant();
        CatalogImportBatch batch = batchRepository.insert(new CatalogImportBatch(
                batchId, storageKey, originalFilename, sha256,
                CatalogImportStatus.PREVIEWED,
                rows.size(),
                newItems.size(), priceChanges.size(), metadataChanges.size(), unchanged,
                actorUserId, now, null, null));

        return new CatalogImportPreview(
                batch.id(), rows.size(), newItems, priceChanges, metadataChanges, unchanged, duplicateCodes);
    }

    private Map<String, CatalogItemView> existingByCode(List<CatalogRow> rows) {
        List<CatalogItemView> found = itemLookup.findByCodes(rows.stream().map(CatalogRow::code).toList());
        Map<String, CatalogItemView> byCode = new HashMap<>();
        for (CatalogItemView view : found) {
            byCode.put(view.code(), view);
        }
        return byCode;
    }

    private static List<String> diffMetadata(CatalogRow row, CatalogItemView current) {
        List<String> changed = new ArrayList<>();
        if (!row.name().equals(current.name())) changed.add("name");
        if (row.type() != current.type()) changed.add("type");
        if (row.billingCategory() != current.billingCategory()) changed.add("billing_category");
        if (!java.util.Objects.equals(nullIfBlank(row.classification()), current.classification())) changed.add("classification");
        return changed;
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
