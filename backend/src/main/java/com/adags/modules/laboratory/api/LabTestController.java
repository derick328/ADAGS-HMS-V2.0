package com.adags.modules.laboratory.api;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 15-lab-technician.md §5.1: the read side of the catalogue Result Entry
// generates its form from, and Lab Catalogue proposals are written
// against. No facility scope — LabTest is a facility-independent catalogue,
// same as the billing service_item catalogue PriceCatalogController reads.
@RestController
@RequestMapping("/api/lab-tests")
public class LabTestController {

    private static final int PLACEHOLDER_SEARCH_LIMIT = 25;

    private final LabTestRepository labTestRepository;
    private final LabTestParameterRepository labTestParameterRepository;
    private final AuthorizationService authorizationService;
    private final BillingFacade billingFacade;

    public LabTestController(
            LabTestRepository labTestRepository,
            LabTestParameterRepository labTestParameterRepository,
            AuthorizationService authorizationService,
            BillingFacade billingFacade
    ) {
        this.labTestRepository = labTestRepository;
        this.labTestParameterRepository = labTestParameterRepository;
        this.authorizationService = authorizationService;
        this.billingFacade = billingFacade;
    }

    @GetMapping
    public List<LabTestResponse> list(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        return labTestRepository.findAllActive().stream()
                .map(test -> LabTestResponse.from(test, labTestParameterRepository.findByTestId(test.id())))
                .toList();
    }

    @GetMapping("/{id}")
    public LabTestResponse get(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        LabTest test = labTestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lab test not found: " + id));
        return LabTestResponse.from(test, labTestParameterRepository.findByTestId(id));
    }

    // V67 admin importer visibility (option 2). Rows the admin imported
    // into the shared service_item catalogue as TYPE=Laboratory that no
    // lab_test classifies yet — returned so the doctor's lab-order
    // typeahead can render them as unselectable placeholders alongside
    // the priced/orderable entries served by /api/price-catalog/search.
    // Excludes any code that already has a real lab_test row so the
    // typeahead never shows two entries for the same code.
    @GetMapping("/pending-classification")
    public List<PendingLabClassificationResponse> pendingClassification(
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        Set<String> classifiedCodes = labTestRepository.findAllActive().stream()
                .map(LabTest::code).collect(Collectors.toSet());
        return billingFacade.findUnclassifiedCatalogueEntries(
                        BillingFacade.CatalogueKind.LABORATORY, query, classifiedCodes, PLACEHOLDER_SEARCH_LIMIT)
                .stream()
                .map(e -> PendingLabClassificationResponse.of(e.serviceItemId(), e.code(), e.name()))
                .toList();
    }

    private void requirePermission(Actor actor) {
        if (!authorizationService.hasPermission(actor, "LAB_QUEUE_READ")
                && !authorizationService.hasPermission(actor, "LAB_RESULT_ENTER")
                && !authorizationService.hasPermission(actor, "LAB_ITEM_PROPOSE")) {
            throw new AccessDeniedException("Missing permission: LAB_QUEUE_READ");
        }
    }
}
