package com.adags.modules.inventory.api;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.inventory.application.ActivateItemService;
import com.adags.modules.inventory.application.ClassifyItemCommand;
import com.adags.modules.inventory.application.ClassifyItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.SetAdministrationWitnessCommand;
import com.adags.modules.inventory.application.SetAdministrationWitnessService;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 12-pharmacist.md §9.2/§10.2: the full NEW_ITEM proposal-and-approval
// workflow is out of scope for this build brief (item proposals are a
// later step); this is the direct-create path gated by ITEM_CLASSIFY —
// pharmacist-in-charge / CLINICAL_LEAD, the same authority who will
// subsequently classify the item's regulatory_schedule. Unscoped permission
// check (no ScopeRef) — the catalogue is not facility-specific (root
// CLAUDE.md "Facility": item is a definition, not a place), same treatment
// as ReferenceDataFacade's ICD-10 synonym review.
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private static final int SEARCH_LIMIT = 20;

    private final CreateItemService createItemService;
    private final ClassifyItemService classifyItemService;
    private final ActivateItemService activateItemService;
    private final SetAdministrationWitnessService setAdministrationWitnessService;
    private final ItemRepository itemRepository;
    private final AuthorizationService authorizationService;
    private final BillingFacade billingFacade;

    public ItemController(
            CreateItemService createItemService,
            ClassifyItemService classifyItemService,
            ActivateItemService activateItemService,
            SetAdministrationWitnessService setAdministrationWitnessService,
            ItemRepository itemRepository,
            AuthorizationService authorizationService,
            BillingFacade billingFacade
    ) {
        this.createItemService = createItemService;
        this.classifyItemService = classifyItemService;
        this.activateItemService = activateItemService;
        this.setAdministrationWitnessService = setAdministrationWitnessService;
        this.itemRepository = itemRepository;
        this.authorizationService = authorizationService;
        this.billingFacade = billingFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@Valid @RequestBody CreateItemRequest request, @AuthenticationPrincipal Actor actor) {
        requireItemClassify(actor);
        Item item = createItemService.create(new CreateItemCommand(request.code(), request.name(),
                request.classification(), request.form(), request.strength(), request.unit(),
                request.reorderLevel(), actor.userId()));
        return ItemResponse.from(item);
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasAnyPermission(actor, "STOCK_READ", "CATALOG_IMPORT")) {
            throw new AccessDeniedException("Missing permission: STOCK_READ");
        }
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + id));
        return ItemResponse.from(item);
    }

    // 13-controlled-medicines-policy.md §4: the prescriber typeahead. Every
    // match is returned, including CONTROLLED items — visible and
    // unselectable with the reason stated (ItemSearchResultResponse),
    // never silently absent. Gated by STOCK_READ or CATALOG_IMPORT; a dedicated formulary-search
    // permission belongs to the prescribing/orders module once it exists.
    //
    // Also surfaces catalogue rows the admin importer (V67) has landed
    // in service_item but which no inventory.item row classifies yet —
    // shown selectable=false with reason "Pending pharmacy
    // classification." Same visibility pattern the CONTROLLED path
    // already uses: the row exists, the doctor knows it exists, and
    // nothing can be prescribed until a pharmacist-in-charge fills in
    // form/unit/regulatory_schedule. Placeholder id is the service_item
    // UUID and will not resolve to an Item on placement, so a bypass
    // client-side would still fail at prescribe time.
    @GetMapping("/search")
    public List<ItemSearchResultResponse> search(@RequestParam String q, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "STOCK_READ")) {
            throw new AccessDeniedException("Missing permission: STOCK_READ");
        }
        List<ItemSearchResultResponse> classified = itemRepository.searchByName(q, SEARCH_LIMIT).stream()
                .map(ItemSearchResultResponse::from).toList();
        int remaining = SEARCH_LIMIT - classified.size();
        if (remaining <= 0) {
            return classified;
        }
        Set<String> excludeCodes = classified.stream()
                .map(ItemSearchResultResponse::code)
                .collect(Collectors.toSet());
        List<BillingFacade.UnclassifiedCatalogueEntry> placeholders =
                billingFacade.findUnclassifiedCatalogueEntries(
                        BillingFacade.CatalogueKind.PHARMACY, q, excludeCodes, remaining);
        List<ItemSearchResultResponse> combined = new ArrayList<>(classified);
        for (BillingFacade.UnclassifiedCatalogueEntry entry : placeholders) {
            combined.add(ItemSearchResultResponse.pendingClassification(
                    entry.serviceItemId(), entry.code(), entry.name()));
        }
        return combined;
    }

    @PostMapping("/{id}/classify")
    public ItemResponse classify(
            @PathVariable UUID id, @Valid @RequestBody ClassifyItemRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requireItemClassify(actor);
        Item item = classifyItemService.classify(
                new ClassifyItemCommand(id, request.regulatorySchedule(), request.reason(), actor.userId()));
        return ItemResponse.from(item);
    }

    @PostMapping("/{id}/activate")
    public ItemResponse activate(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requireItemClassify(actor);
        return ItemResponse.from(activateItemService.activate(id));
    }

    @PostMapping("/{id}/administration-witness")
    public ItemResponse setAdministrationWitness(
            @PathVariable UUID id, @Valid @RequestBody SetAdministrationWitnessRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requireItemClassify(actor);
        Item item = setAdministrationWitnessService.setRequiresWitness(
                new SetAdministrationWitnessCommand(id, request.requiresWitness()));
        return ItemResponse.from(item);
    }

    private void requireItemClassify(Actor actor) {
        if (!authorizationService.hasPermission(actor, "ITEM_CLASSIFY")) {
            throw new AccessDeniedException("Missing permission: ITEM_CLASSIFY");
        }
    }
}
