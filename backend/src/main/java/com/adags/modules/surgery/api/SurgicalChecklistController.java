package com.adags.modules.surgery.api;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.surgery.application.CompleteChecklistCommand;
import com.adags.modules.surgery.application.CompleteChecklistService;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgicalChecklist;
import com.adags.modules.surgery.domain.SurgicalChecklistRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// SURGERY_EPISODE_DOCUMENT — same permission as procedure-item prepare/
// reconcile/attest, held by both SPECIALIST_DOCTOR and WARD_NURSE, since
// either can run a checklist pause depending on facility protocol.
@RestController
@RequestMapping("/api/surgical-checklists")
public class SurgicalChecklistController {

    private final CompleteChecklistService completeChecklistService;
    private final SurgicalChecklistRepository surgicalChecklistRepository;
    private final SurgeryOrderRepository surgeryOrderRepository;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public SurgicalChecklistController(
            CompleteChecklistService completeChecklistService,
            SurgicalChecklistRepository surgicalChecklistRepository,
            SurgeryOrderRepository surgeryOrderRepository,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.completeChecklistService = completeChecklistService;
        this.surgicalChecklistRepository = surgicalChecklistRepository;
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SurgicalChecklistResponse complete(
            @Valid @RequestBody CompleteChecklistRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, request.surgeryOrderId());
        CompleteChecklistCommand command = new CompleteChecklistCommand(request.surgeryOrderId(), request.phase(),
                request.items(), request.countCorrect(), request.countDiscrepancyNote(),
                request.isEmergencyReduced(), actor.userId());
        SurgicalChecklist checklist = completeChecklistService.complete(command);
        return SurgicalChecklistResponse.from(checklist);
    }

    @GetMapping
    public List<SurgicalChecklistResponse> listBySurgeryOrder(
            @RequestParam UUID surgeryOrderId, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, surgeryOrderId);
        return surgicalChecklistRepository.findBySurgeryOrderId(surgeryOrderId).stream()
                .map(SurgicalChecklistResponse::from).toList();
    }

    private void requirePermission(Actor actor, UUID surgeryOrderId) {
        SurgeryOrder order = surgeryOrderRepository.findById(surgeryOrderId)
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + surgeryOrderId));
        EncounterContext context = encounterFacade.findContext(order.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + order.encounterId()));
        if (!authorizationService.hasPermission(
                actor, "SURGERY_EPISODE_DOCUMENT", ScopeRef.of(ScopeType.DEPARTMENT, context.departmentId()))) {
            throw new AccessDeniedException("Missing permission: SURGERY_EPISODE_DOCUMENT");
        }
    }
}
