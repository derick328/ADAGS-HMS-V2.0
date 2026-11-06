package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.CollectSpecimenCommand;
import com.adags.modules.laboratory.application.CollectSpecimenService;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

// 15-lab-technician.md §4: accession + quality assessment ("accept
// sample"). Phase 4 build brief item 3: THE GATE AT SPECIMEN COLLECTION —
// CollectSpecimenService calls BillingFacade.authorizeConsumption before
// anything is persisted; an unsatisfied gate throws BillingGateRejectedException,
// which GlobalExceptionHandler already turns into a 402 carrying the
// outstanding amount and service name (backend/CLAUDE.md "API").
@RestController
@RequestMapping("/api/specimens")
public class SpecimenController {

    private final CollectSpecimenService collectSpecimenService;
    private final LabOrderRepository labOrderRepository;
    private final AuthorizationService authorizationService;

    public SpecimenController(
            CollectSpecimenService collectSpecimenService,
            LabOrderRepository labOrderRepository,
            AuthorizationService authorizationService
    ) {
        this.collectSpecimenService = collectSpecimenService;
        this.labOrderRepository = labOrderRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpecimenResponse collect(@Valid @RequestBody CollectSpecimenRequest request, @AuthenticationPrincipal Actor actor) {
        LabOrder order = labOrderRepository.findById(request.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + request.labOrderId()));
        // §12: SPECIMEN_ACCEPT/SPECIMEN_REJECT are one permission in
        // practice here since accept-with-quality-assessment is a single
        // action (see V21__lab_order_and_specimen.sql) — SPECIMEN_ACCEPT
        // covers it regardless of the resulting quality outcome.
        if (!authorizationService.hasPermission(
                actor, "SPECIMEN_ACCEPT", ScopeRef.of(ScopeType.FACILITY, order.facilityId()))) {
            throw new AccessDeniedException("Missing permission: SPECIMEN_ACCEPT");
        }

        Specimen specimen = collectSpecimenService.collect(new CollectSpecimenCommand(
                request.labOrderId(), request.quality(), request.qualityNote(),
                request.sampleType(), request.sampleTypeOverrideReason(), actor.userId()));
        return SpecimenResponse.from(specimen);
    }
}
