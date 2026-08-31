package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogImportBatch;
import com.adags.modules.billing.domain.CatalogImportBatchRepository;
import com.adags.modules.billing.domain.CatalogImportStatus;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceList;
import com.adags.modules.billing.domain.PriceListEntry;
import com.adags.modules.billing.domain.PriceListEntryRepository;
import com.adags.modules.billing.domain.PriceListRepository;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// Phase 5/9 catalogue import — stage 2 (apply). Re-parses the archived
// .xlsx and writes the diff in one transaction, then flips the batch
// APPLIED. Failure at any row aborts the whole run — the file is one
// atomic unit (root CLAUDE.md "Billing": no partial writes on
// resource-level operations).
//
// Price change path is the mandated "close-then-insert": we set
// valid_to = today on the current row and INSERT a new row with
// valid_from = today. The partial unique index on
// (price_list_id, service_item_id) WHERE valid_to IS NULL guarantees at
// most one current row per pair. Never UPDATE amount_minor.
//
// PRODUCT CODEs missing from the new file are ignored (user decision:
// option (a)) — nothing is soft-deleted.
@Service
public class ApplyCatalogImportService {

    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");
    private static final String CURRENCY = "TZS";

    private final CatalogRowParserPort parser;
    private final CatalogFileStoragePort storage;
    private final CatalogItemLookupPort itemLookup;
    private final CatalogImportBatchRepository batchRepository;
    private final PriceListRepository priceListRepository;
    private final PriceListEntryRepository priceListEntryRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ApplyCatalogImportService(
            CatalogRowParserPort parser,
            CatalogFileStoragePort storage,
            CatalogItemLookupPort itemLookup,
            CatalogImportBatchRepository batchRepository,
            PriceListRepository priceListRepository,
            PriceListEntryRepository priceListEntryRepository,
            DomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.parser = parser;
        this.storage = storage;
        this.itemLookup = itemLookup;
        this.batchRepository = batchRepository;
        this.priceListRepository = priceListRepository;
        this.priceListEntryRepository = priceListEntryRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public CatalogImportBatch apply(UUID batchId, UUID actorUserId) {
        CatalogImportBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Catalog import batch not found: " + batchId));
        if (batch.status() != CatalogImportStatus.PREVIEWED) {
            throw new IllegalStateException(
                    "Batch " + batchId + " is " + batch.status() + " — only PREVIEWED batches can be applied.");
        }

        List<CatalogRow> rawRows = parser.parse(storage.open(batch.storageKey()));
        // Same disambiguation the preview stage ran; deterministic on the
        // same input file, so the effective codes here match what the
        // admin saw and approved.
        List<CatalogRow> rows = CatalogCodeResolver.resolve(rawRows).resolvedRows();

        Map<String, CatalogItemView> existing = new HashMap<>();
        for (CatalogItemView view : itemLookup.findByCodes(rows.stream().map(CatalogRow::code).toList())) {
            existing.put(view.code(), view);
        }

        PriceList cashList = priceListRepository.findByPayerCategory(PayerCategory.CASH)
                .orElseThrow(() -> new IllegalStateException("CASH price list missing — seed data from V15 not applied."));
        LocalDate today = LocalDate.ofInstant(clock.instant(), FACILITY_ZONE);
        Instant now = clock.instant();

        int newItems = 0;
        int priceChanges = 0;
        int metadataChanges = 0;
        int unchanged = 0;

        for (CatalogRow row : rows) {
            CatalogItemView current = existing.get(row.code());
            if (current == null) {
                UUID itemId = itemLookup.insertNewCatalogItem(row);
                if (row.isPriced()) {
                    priceListEntryRepository.insert(new PriceListEntry(
                            UUID.randomUUID(), cashList.id(), itemId,
                            new Money(row.priceMinor(), CURRENCY),
                            today, null, actorUserId, now));
                }
                // Unpriced new item: service_item row inserted with the
                // metadata but no price_list_entry. The billing gate will
                // refuse to charge it until a later upload provides a
                // real price.
                newItems++;
                continue;
            }

            // Unpriced incoming rows never touch the existing price —
            // "fix later" semantics.
            boolean priceChanged = row.isPriced()
                    && (current.currentCashPriceMinor() == null
                        || current.currentCashPriceMinor() != row.priceMinor());
            boolean metadataChanged = !metadataMatches(row, current);

            if (metadataChanged) {
                // Also flips is_active = true so a fresh upload revives
                // any items that were soft-deactivated via
                // "Deactivate all" — a re-upload of the same PRODUCT
                // CODE brings the row back into the catalogue.
                itemLookup.updateMetadata(current.serviceItemId(), row);
                metadataChanges++;
            }
            if (priceChanged) {
                // Close old (if any) then open new — same close-then-insert
                // pattern the pharmacy proposal review already uses.
                priceListEntryRepository.findCurrent(current.serviceItemId(), PayerCategory.CASH)
                        .ifPresent(entry -> priceListEntryRepository.close(entry.id(), today));
                priceListEntryRepository.insert(new PriceListEntry(
                        UUID.randomUUID(), cashList.id(), current.serviceItemId(),
                        new Money(row.priceMinor(), CURRENCY),
                        today, null, actorUserId, now));
                priceChanges++;
            }
            if (!priceChanged && !metadataChanged) {
                unchanged++;
            }
        }

        CatalogImportBatch applied = batchRepository.markApplied(
                batchId, actorUserId, newItems, priceChanges, metadataChanges, unchanged);

        // Outbox — a downstream cache or read-model refresh subscribes here.
        eventPublisher.publish("CatalogImported", Map.of(
                "batchId", batchId.toString(),
                "newItems", Integer.toString(newItems),
                "priceChanges", Integer.toString(priceChanges),
                "metadataChanges", Integer.toString(metadataChanges)));

        return applied;
    }

    private static boolean metadataMatches(CatalogRow row, CatalogItemView current) {
        if (!row.name().equals(current.name())) return false;
        if (row.type() != current.type()) return false;
        if (row.billingCategory() != current.billingCategory()) return false;
        return java.util.Objects.equals(nullIfBlank(row.classification()), current.classification());
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
