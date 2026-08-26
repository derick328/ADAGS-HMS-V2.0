package com.adags.modules.laboratory.api;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 15-lab-technician.md §5.1: the read side of the catalogue Result Entry
// generates its form from, and Lab Catalogue proposals are written
// against. No facility scope — LabTest is a facility-independent catalogue,
// same as the billing service_item catalogue PriceCatalogController reads.
@RestController
@RequestMapping("/api/lab-tests")
public class LabTestController {

    private final LabTestRepository labTestRepository;
    private final LabTestParameterRepository labTestParameterRepository;
    private final AuthorizationService authorizationService;

    public LabTestController(
            LabTestRepository labTestRepository,
            LabTestParameterRepository labTestParameterRepository,
            AuthorizationService authorizationService
    ) {
        this.labTestRepository = labTestRepository;
        this.labTestParameterRepository = labTestParameterRepository;
        this.authorizationService = authorizationService;
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

    private void requirePermission(Actor actor) {
        if (!authorizationService.hasPermission(actor, "LAB_QUEUE_READ")
                && !authorizationService.hasPermission(actor, "LAB_RESULT_ENTER")
                && !authorizationService.hasPermission(actor, "LAB_ITEM_PROPOSE")) {
            throw new AccessDeniedException("Missing permission: LAB_QUEUE_READ");
        }
    }
}
