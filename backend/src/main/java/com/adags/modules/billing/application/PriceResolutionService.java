package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceListEntryRepository;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;

// 02-billing-architecture.md §4.2 / root CLAUDE.md "Billing": "Nobody
// types a price... A missing price for the applicable payer blocks with
// an explicit error — it never defaults to zero." This is the one seam
// every charge-posting caller (item 5's triage charge, later order/
// prescription charges) resolves a price through — never a raw
// PriceListEntry lookup at the call site.
@Service
public class PriceResolutionService {

    private final ServiceItemRepository serviceItemRepository;
    private final PriceListEntryRepository priceListEntryRepository;

    public PriceResolutionService(ServiceItemRepository serviceItemRepository, PriceListEntryRepository priceListEntryRepository) {
        this.serviceItemRepository = serviceItemRepository;
        this.priceListEntryRepository = priceListEntryRepository;
    }

    public Money resolvePrice(String serviceItemCode, PayerCategory payerCategory) {
        ServiceItem serviceItem = serviceItemRepository.findByCode(serviceItemCode)
                .filter(ServiceItem::isActive)
                .orElseThrow(() -> new PriceNotConfiguredException(
                        "No active service item with code '" + serviceItemCode + "'."));

        return priceListEntryRepository.findCurrent(serviceItem.id(), payerCategory)
                .map(entry -> entry.amount())
                .orElseThrow(() -> new PriceNotConfiguredException(
                        "No current price for '" + serviceItemCode + "' under payer category " + payerCategory + "."));
    }
}
