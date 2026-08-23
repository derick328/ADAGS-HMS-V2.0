package com.adags.modules.surgery.api;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.surgery.application.AttestProcedureItemCommand;
import com.adags.modules.surgery.application.AttestProcedureItemService;
import com.adags.modules.surgery.application.PrepareProcedureItemCommand;
import com.adags.modules.surgery.application.PrepareProcedureItemService;
import com.adags.modules.surgery.application.ReconcileProcedureItemCommand;
import com.adags.modules.surgery.application.ReconcileProcedureItemService;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedureItemRepository;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// SURGERY_EPISODE_DOCUMENT (V5): "Document a surgical episode" — granted to
// both SPECIALIST_DOCTOR and WARD_NURSE (04-*.md §2.2: "prepared by the
// doctor or the ward nurse, as specified"), same permission item 1's
// controller uses for read access to the order.
@RestController
@RequestMapping("/api/procedure-items")
public class ProcedureItemController {

    private final PrepareProcedureItemService prepareProcedureItemService;
    private final ReconcileProcedureItemService reconcileProcedureItemService;
    private final AttestProcedureItemService attestProcedureItemService;
    private final ProcedureItemRepository procedureItemRepository;
    private final SurgeryOrderRepository surgeryOrderRepository;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public ProcedureItemController(
            PrepareProcedureItemService prepareProcedureItemService,
            ReconcileProcedureItemService reconcileProcedureItemService,
            AttestProcedureItemService attestProcedureItemService,
            ProcedureItemRepository procedureItemRepository,
            SurgeryOrderRepository surgeryOrderRepository,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.prepareProcedureItemService = prepareProcedureItemService;
        this.reconcileProcedureItemService = reconcileProcedureItemService;
        this.attestProcedureItemService = attestProcedureItemService;
        this.procedureItemRepository = procedureItemRepository;
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProcedureItemResponse prepare(
            @Valid @RequestBody PrepareProcedureItemRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "SURGERY_EPISODE_DOCUMENT", request.surgeryOrderId());

        PrepareProcedureItemCommand command = new PrepareProcedureItemCommand(request.surgeryOrderId(),
                request.itemId(), request.phase(), request.quantityPrepared(), request.batchId(), actor.userId());
        ProcedureItem item = prepareProcedureItemService.prepare(command);
        return ProcedureItemResponse.from(item);
    }

    // Circulating nurse's half of reconciliation.
    @PostMapping("/{id}/reconcile")
    public ProcedureItemResponse reconcile(
            @PathVariable UUID id, @Valid @RequestBody ReconcileProcedureItemRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        ProcedureItem existing = procedureItemRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Procedure item not found: " + id));
        requirePermission(actor, "SURGERY_EPISODE_DOCUMENT", existing.surgeryOrderId());

        ReconcileProcedureItemCommand command = new ReconcileProcedureItemCommand(id, request.quantityUsed(),
                request.quantityReturned(), request.quantityWasted(), request.wastageReason(), actor.userId());
        return ProcedureItemResponse.from(reconcileProcedureItemService.reconcile(command));
    }

    // Surgeon's half — attests to the counts the circulating nurse already
    // entered. Must be a different person (ProcedureItemSelfReconciliation
    // NotAllowedException).
    @PostMapping("/{id}/attest")
    public ProcedureItemResponse attest(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        ProcedureItem existing = procedureItemRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Procedure item not found: " + id));
        requirePermission(actor, "SURGERY_EPISODE_DOCUMENT", existing.surgeryOrderId());

        return ProcedureItemResponse.from(
                attestProcedureItemService.attest(new AttestProcedureItemCommand(id, actor.userId())));
    }

    @GetMapping
    public List<ProcedureItemResponse> listBySurgeryOrder(
            @RequestParam UUID surgeryOrderId, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "SURGERY_EPISODE_DOCUMENT", surgeryOrderId);
        return procedureItemRepository.findBySurgeryOrderId(surgeryOrderId).stream()
                .map(ProcedureItemResponse::from).toList();
    }

    private void requirePermission(Actor actor, String permissionCode, UUID surgeryOrderId) {
        SurgeryOrder order = surgeryOrderRepository.findById(surgeryOrderId)
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + surgeryOrderId));
        EncounterContext context = encounterFacade.findContext(order.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + order.encounterId()));
        if (!authorizationService.hasPermission(
                actor, permissionCode, ScopeRef.of(ScopeType.DEPARTMENT, context.departmentId()))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
