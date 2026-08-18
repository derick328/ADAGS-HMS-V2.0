package com.adags.modules.pharmacy.api;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.pharmacy.application.PlacePrescriptionCommand;
import com.adags.modules.pharmacy.application.PlacePrescriptionService;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
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

// D1 (12-pharmacist.md §1): PRESCRIPTION_CREATE — doctors only. No
// substitution, no doctor UI beyond this one action; see
// PlacePrescriptionService's comment for why this exists at all inside a
// build brief about dispensing.
@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PlacePrescriptionService placePrescriptionService;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public PrescriptionController(
            PlacePrescriptionService placePrescriptionService,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.placePrescriptionService = placePrescriptionService;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionLineResponse place(@Valid @RequestBody PlacePrescriptionRequest request, @AuthenticationPrincipal Actor actor) {
        EncounterContext encounterContext = encounterFacade.findContext(request.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + request.encounterId()));
        if (!authorizationService.hasPermission(
                actor, "PRESCRIPTION_CREATE", ScopeRef.of(ScopeType.FACILITY, encounterContext.facilityId()))) {
            throw new AccessDeniedException("Missing permission: PRESCRIPTION_CREATE");
        }

        PrescriptionLine line = placePrescriptionService.placeOrder(new PlacePrescriptionCommand(
                request.encounterId(), request.itemId(), request.quantityPrescribed(), request.dosageInstructions(),
                request.substitutionAllowed(), actor.userId()));
        return PrescriptionLineResponse.from(line);
    }
}
