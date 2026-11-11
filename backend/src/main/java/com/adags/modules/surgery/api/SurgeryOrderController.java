package com.adags.modules.surgery.api;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.surgery.application.CancelSurgeryOrderCommand;
import com.adags.modules.surgery.application.CancelSurgeryOrderService;
import com.adags.modules.surgery.application.CompleteSurgeryOrderCommand;
import com.adags.modules.surgery.application.CompleteSurgeryOrderService;
import com.adags.modules.surgery.application.CreateEmergencyDraftSurgeryOrderCommand;
import com.adags.modules.surgery.application.CreateEmergencyDraftSurgeryOrderService;
import com.adags.modules.surgery.application.ReconcileEmergencyDraftProcedureNameCommand;
import com.adags.modules.surgery.application.ReconcileEmergencyDraftProcedureNameService;
import com.adags.modules.surgery.application.RecordSurgeryDispositionCommand;
import com.adags.modules.surgery.application.RecordSurgeryDispositionService;
import com.adags.modules.surgery.application.RevokeConsentCommand;
import com.adags.modules.surgery.application.RevokeConsentService;
import com.adags.modules.surgery.application.ScheduleSurgeryCommand;
import com.adags.modules.surgery.application.ScheduleSurgeryService;
import com.adags.modules.surgery.application.StartSurgeryOrderCommand;
import com.adags.modules.surgery.application.StartSurgeryOrderService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/surgery-orders")
public class SurgeryOrderController {

    private final ScheduleSurgeryService scheduleSurgeryService;
    private final StartSurgeryOrderService startSurgeryOrderService;
    private final CompleteSurgeryOrderService completeSurgeryOrderService;
    private final RecordSurgeryDispositionService recordSurgeryDispositionService;
    private final CancelSurgeryOrderService cancelSurgeryOrderService;
    private final CreateEmergencyDraftSurgeryOrderService createEmergencyDraftSurgeryOrderService;
    private final ReconcileEmergencyDraftProcedureNameService reconcileEmergencyDraftProcedureNameService;
    private final RevokeConsentService revokeConsentService;
    private final SurgeryOrderRepository surgeryOrderRepository;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public SurgeryOrderController(
            ScheduleSurgeryService scheduleSurgeryService,
            StartSurgeryOrderService startSurgeryOrderService,
            CompleteSurgeryOrderService completeSurgeryOrderService,
            RecordSurgeryDispositionService recordSurgeryDispositionService,
            CancelSurgeryOrderService cancelSurgeryOrderService,
            CreateEmergencyDraftSurgeryOrderService createEmergencyDraftSurgeryOrderService,
            ReconcileEmergencyDraftProcedureNameService reconcileEmergencyDraftProcedureNameService,
            RevokeConsentService revokeConsentService,
            SurgeryOrderRepository surgeryOrderRepository,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.scheduleSurgeryService = scheduleSurgeryService;
        this.startSurgeryOrderService = startSurgeryOrderService;
        this.completeSurgeryOrderService = completeSurgeryOrderService;
        this.recordSurgeryDispositionService = recordSurgeryDispositionService;
        this.cancelSurgeryOrderService = cancelSurgeryOrderService;
        this.createEmergencyDraftSurgeryOrderService = createEmergencyDraftSurgeryOrderService;
        this.reconcileEmergencyDraftProcedureNameService = reconcileEmergencyDraftProcedureNameService;
        this.revokeConsentService = revokeConsentService;
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    // SURGERY_ORDER_CREATE (V5), granted to SPECIALIST_DOCTOR — scoped to
    // the encounter's department, same pattern as AdmissionController's
    // ADMISSION_ORDER check.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SurgeryOrderResponse schedule(
            @Valid @RequestBody ScheduleSurgeryRequest request, @AuthenticationPrincipal Actor actor
    ) {
        EncounterContext context = encounterFacade.findContext(request.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + request.encounterId()));
        requirePermission(actor, "SURGERY_ORDER_CREATE", context.scope());

        ScheduleSurgeryCommand command = new ScheduleSurgeryCommand(
                request.encounterId(), request.procedureServiceItemCode(), request.surgeryType(), request.urgency(),
                request.anesthesiaType(), request.scheduledDate(), request.timeSlot(), request.theatreId(),
                request.estimatedDurationMinutes(), request.assignedNurses(), request.preOpNotes(),
                request.consentObtained(), request.consentDocumentKey(), actor.userId());
        SurgeryOrder order = scheduleSurgeryService.schedule(command);
        return SurgeryOrderResponse.from(order);
    }

    // 17-ward-nurse.md §10: the Surgery Queue's source list — unscoped, like
    // LabOrderController#queue: SURGERY_EPISODE_DOCUMENT is granted at
    // DEPARTMENT scope, but a facility runs one theatre list shared across
    // departments, so the permission alone gates the browse and the client
    // buckets/highlights (e.g. orders carrying this nurse in assignedNurses)
    // the same way every other QueueShell screen does.
    @GetMapping
    public List<SurgeryOrderResponse> listActive(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "SURGERY_EPISODE_DOCUMENT")) {
            throw new AccessDeniedException("Missing permission: SURGERY_EPISODE_DOCUMENT");
        }
        return surgeryOrderRepository.findActive().stream().map(SurgeryOrderResponse::from).toList();
    }

    // SURGERY_EPISODE_DOCUMENT (V5): granted to both SPECIALIST_DOCTOR
    // (who also holds SURGERY_ORDER_CREATE) and WARD_NURSE — the
    // circulating nurse's own read access to the order she'll later
    // reconcile against.
    @GetMapping("/{id}")
    public SurgeryOrderResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        SurgeryOrder order = surgeryOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + id));
        EncounterContext context = encounterFacade.findContext(order.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + order.encounterId()));
        requirePermission(actor, "SURGERY_EPISODE_DOCUMENT", context.scope());
        return SurgeryOrderResponse.from(order);
    }

    // Phase 8 item 4: SCHEDULED -> IN_PROGRESS, the checklist/consent/
    // billing-gate procedure-start check.
    @PostMapping("/{id}/start")
    public SurgeryOrderResponse start(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermissionForOrder(actor, "SURGERY_EPISODE_DOCUMENT", id);
        SurgeryOrder started = startSurgeryOrderService.start(new StartSurgeryOrderCommand(id, actor.userId()));
        return SurgeryOrderResponse.from(started);
    }

    // Task text (item 1): consent may be revoked between scheduling and
    // procedure start — StartSurgeryOrderService checks it live.
    @PostMapping("/{id}/revoke-consent")
    public void revokeConsent(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermissionForOrder(actor, "SURGERY_EPISODE_DOCUMENT", id);
        revokeConsentService.revoke(new RevokeConsentCommand(id, actor.userId()));
    }

    // Phase 8 item 3: "COMPLETED gates payment — send for payment only
    // after." Same SURGERY_EPISODE_DOCUMENT permission as reconcile/attest
    // — completion is finalising the episode's documentation, not a
    // separate clinical action.
    @PostMapping("/{id}/complete")
    public SurgeryOrderResponse complete(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermissionForOrder(actor, "SURGERY_EPISODE_DOCUMENT", id);
        SurgeryOrder completed = completeSurgeryOrderService.complete(new CompleteSurgeryOrderCommand(id, actor.userId()));
        return SurgeryOrderResponse.from(completed);
    }

    // Phase 8 item 5: "Procedure COMPLETE -> disposition."
    @PostMapping("/{id}/disposition")
    public SurgeryOrderResponse recordDisposition(
            @PathVariable UUID id, @Valid @RequestBody RecordSurgeryDispositionRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermissionForOrder(actor, "SURGERY_EPISODE_DOCUMENT", id);
        SurgeryOrder updated = recordSurgeryDispositionService.recordDisposition(new RecordSurgeryDispositionCommand(
                id, request.disposition(), request.orderedWardClass(), actor.userId()));
        return SurgeryOrderResponse.from(updated);
    }

    // Phase 8 item 6: cancellation.
    @PostMapping("/{id}/cancel")
    public SurgeryOrderResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody CancelSurgeryOrderRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermissionForOrder(actor, "SURGERY_ORDER_CREATE", id);
        SurgeryOrder cancelled = cancelSurgeryOrderService.cancel(
                new CancelSurgeryOrderCommand(id, request.reason(), actor.userId()));
        return SurgeryOrderResponse.from(cancelled);
    }

    // Phase 8 item 8: "urgency = IMMEDIATE -> status = DRAFT_EMERGENCY."
    // Same SURGERY_ORDER_CREATE permission as normal scheduling — this is
    // still the surgeon booking a case, just retrospectively/urgently.
    @PostMapping("/emergency-draft")
    @ResponseStatus(HttpStatus.CREATED)
    public SurgeryOrderResponse createEmergencyDraft(
            @Valid @RequestBody CreateEmergencyDraftSurgeryOrderRequest request, @AuthenticationPrincipal Actor actor
    ) {
        EncounterContext context = encounterFacade.findContext(request.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + request.encounterId()));
        requirePermission(actor, "SURGERY_ORDER_CREATE", context.scope());

        SurgeryOrder order = createEmergencyDraftSurgeryOrderService.create(new CreateEmergencyDraftSurgeryOrderCommand(
                request.encounterId(), request.procedureName(), request.surgeryType(), request.anesthesiaType(),
                request.scheduledDate(), request.timeSlot(), request.theatreId(), request.estimatedDurationMinutes(),
                request.assignedNurses(), request.preOpNotes(), actor.userId()));
        return SurgeryOrderResponse.from(order);
    }

    // Phase 8 item 8: "Free-text procedure names must be reconciled to a
    // catalogue item before the charge can be finalised."
    @PostMapping("/{id}/reconcile-emergency-draft")
    public SurgeryOrderResponse reconcileEmergencyDraft(
            @PathVariable UUID id, @Valid @RequestBody ReconcileEmergencyDraftProcedureNameRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermissionForOrder(actor, "SURGERY_ORDER_CREATE", id);
        SurgeryOrder reconciled = reconcileEmergencyDraftProcedureNameService.reconcile(
                new ReconcileEmergencyDraftProcedureNameCommand(id, request.procedureServiceItemCode(), actor.userId()));
        return SurgeryOrderResponse.from(reconciled);
    }

    private void requirePermissionForOrder(Actor actor, String permissionCode, UUID surgeryOrderId) {
        SurgeryOrder existing = surgeryOrderRepository.findById(surgeryOrderId)
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + surgeryOrderId));
        EncounterContext context = encounterFacade.findContext(existing.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + existing.encounterId()));
        requirePermission(actor, permissionCode, context.scope());
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeType scopeType, UUID scopeId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(scopeType, scopeId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeRef scope) {
        if (!authorizationService.hasPermission(actor, permissionCode, scope)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
