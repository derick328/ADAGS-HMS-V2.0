package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceList;
import com.adags.modules.billing.domain.PriceListEntry;
import com.adags.modules.billing.domain.PriceListEntryRepository;
import com.adags.modules.billing.domain.PriceListRepository;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemCategory;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

// 12-pharmacist.md §9.2: what an approved PharmacyItemProposal (NEW_ITEM's
// first price, or a PRICE_CHANGE) actually does to the price list —
// root CLAUDE.md "Billing": "Prices are versioned, never edited in place.
// Close valid_to, open a new row." CASH only for now, matching every
// existing pricing fixture/precedent in this codebase — NHIF/PRIVATE_SCHEME/
// CORPORATE/STAFF are configuration targets an admin fills in later
// (V15__service_item_and_price_list.sql), not something a pharmacy item
// proposal drives.
@Service
public class SetPharmacyItemPriceService {

    private static final String CURRENCY_CODE = "TZS";

    private final ServiceItemRepository serviceItemRepository;
    private final PriceListRepository priceListRepository;
    private final PriceListEntryRepository priceListEntryRepository;

    public SetPharmacyItemPriceService(
            ServiceItemRepository serviceItemRepository,
            PriceListRepository priceListRepository,
            PriceListEntryRepository priceListEntryRepository
    ) {
        this.serviceItemRepository = serviceItemRepository;
        this.priceListRepository = priceListRepository;
        this.priceListEntryRepository = priceListEntryRepository;
    }

    @Transactional
    public PriceListEntry setPrice(SetPharmacyItemPriceCommand command) {
        ServiceItem serviceItem = serviceItemRepository.findByCode(command.itemCode())
                .orElseGet(() -> serviceItemRepository.insert(
                        new ServiceItem(null, command.itemCode(), command.itemName(), ServiceItemCategory.PHARMACY, true)));

        PriceList cashList = priceListRepository.findByPayerCategory(PayerCategory.CASH)
                .orElseThrow(() -> new NoSuchElementException("No CASH price list configured."));

        priceListEntryRepository.findCurrent(serviceItem.id(), PayerCategory.CASH)
                .ifPresent(current -> priceListEntryRepository.close(current.id(), LocalDate.now()));

        PriceListEntry entry = new PriceListEntry(null, cashList.id(), serviceItem.id(),
                new Money(command.priceAmountMinor(), CURRENCY_CODE), null, null, command.actorUserId(), null);
        return priceListEntryRepository.insert(entry);
    }

    public Optional<Money> findCurrentPrice(String itemCode) {
        return serviceItemRepository.findByCode(itemCode)
                .flatMap(serviceItem -> priceListEntryRepository.findCurrent(serviceItem.id(), PayerCategory.CASH))
                .map(PriceListEntry::amount);
    }
}
