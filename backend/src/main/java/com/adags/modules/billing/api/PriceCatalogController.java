package com.adags.modules.billing.api;

import com.adags.modules.billing.application.PriceResolutionService;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.Money;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 02-billing-architecture.md §4.3 / 05-receptionist.md §7.2 ⚠12: "the price
// catalogue is authoritative, not a convenience autofill... payer-aware —
// returns the price that will actually be charged, so the desk quote
// matches the invoice." Items with no price for the resolved payer are
// dropped from the results entirely (⚠12: "blocked, not defaulted to
// zero") rather than returned with a null price the UI would have to know
// not to let the receptionist select.
@RestController
@RequestMapping("/api/price-catalog")
public class PriceCatalogController {

    private final ServiceItemRepository serviceItemRepository;
    private final PriceResolutionService priceResolutionService;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public PriceCatalogController(
            ServiceItemRepository serviceItemRepository,
            PriceResolutionService priceResolutionService,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.serviceItemRepository = serviceItemRepository;
        this.priceResolutionService = priceResolutionService;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/search")
    public List<PriceCatalogItemResponse> search(
            @RequestParam("q") String query,
            @RequestParam(required = false) UUID encounterId,
            @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "PRICE_CATALOG_READ")) {
            throw new AccessDeniedException("Missing permission: PRICE_CATALOG_READ");
        }
        PayerCategory payerCategory = resolvePayerCategory(encounterId);

        List<PriceCatalogItemResponse> results = new ArrayList<>();
        for (ServiceItem item : serviceItemRepository.search(query)) {
            try {
                Money price = priceResolutionService.resolvePrice(item.code(), payerCategory);
                results.add(PriceCatalogItemResponse.of(item, price.amountMinor(), price.currencyCode()));
            } catch (PriceNotConfiguredException noPriceYet) {
                // Excluded, not returned with a null price — ⚠12.
            }
        }
        return results;
    }

    private PayerCategory resolvePayerCategory(UUID encounterId) {
        if (encounterId == null) {
            return PayerCategory.CASH;
        }
        EncounterContext context = encounterFacade.findContext(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        return PayerCategory.fromPayerType(context.payerType());
    }
}
