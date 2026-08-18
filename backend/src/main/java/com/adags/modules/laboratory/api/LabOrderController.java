package com.adags.modules.laboratory.api;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.laboratory.application.PlaceLabOrderCommand;
import com.adags.modules.laboratory.application.PlaceLabOrderService;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
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

// 15-lab-technician.md §4/§12: LAB_ORDER_CREATE, doctors only. Scoped at
// FACILITY — a lab serves a whole facility, not one ward or department
// (root CLAUDE.md "Authorisation": scope resolution walks WARD ->
// DEPARTMENT -> FACILITY, so a facility-scoped assignment is the right
// granularity here, same as 21-multi-facility.md §3's isolation concern).
@RestController
@RequestMapping("/api/lab-orders")
public class LabOrderController {

    private final PlaceLabOrderService placeLabOrderService;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public LabOrderController(
            PlaceLabOrderService placeLabOrderService,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.placeLabOrderService = placeLabOrderService;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabOrderResponse place(@Valid @RequestBody PlaceLabOrderRequest request, @AuthenticationPrincipal Actor actor) {
        EncounterContext encounterContext = encounterFacade.findContext(request.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + request.encounterId()));
        if (!authorizationService.hasPermission(
                actor, "LAB_ORDER_CREATE", ScopeRef.of(ScopeType.FACILITY, encounterContext.facilityId()))) {
            throw new AccessDeniedException("Missing permission: LAB_ORDER_CREATE");
        }

        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                request.encounterId(), request.labTestCode(), request.clinicalIndication(),
                request.urgency(), actor.userId()));
        return LabOrderResponse.from(order);
    }
}
