package com.adags.modules.laboratory.api;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.identity.IdentityFacade;
import com.adags.modules.laboratory.application.PlaceLabOrderCommand;
import com.adags.modules.laboratory.application.PlaceLabOrderService;
import com.adags.modules.laboratory.application.LabResultViewStateService;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenRepository;
import com.adags.modules.laboratory.domain.SpecimenStatus;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.PatientIdentity;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 15-lab-technician.md §4/§12: LAB_ORDER_CREATE, doctors only. Scoped at
// FACILITY — a lab serves a whole facility, not one ward or department
// (root CLAUDE.md "Authorisation": scope resolution walks WARD ->
// DEPARTMENT -> FACILITY, so a facility-scoped assignment is the right
// granularity here, same as 21-multi-facility.md §3's isolation concern).
@RestController
@RequestMapping("/api/lab-orders")
public class LabOrderController {

    private final PlaceLabOrderService placeLabOrderService;
    private final LabOrderRepository labOrderRepository;
    private final LabTestRepository labTestRepository;
    private final SpecimenRepository specimenRepository;
    private final LabResultRepository labResultRepository;
    private final BillingFacade billingFacade;
    private final EncounterFacade encounterFacade;
    private final PatientFacade patientFacade;
    private final AuthorizationService authorizationService;
    private final LabResultViewStateService viewState;
    private final IdentityFacade identityFacade;

    public LabOrderController(
            PlaceLabOrderService placeLabOrderService,
            LabOrderRepository labOrderRepository,
            LabTestRepository labTestRepository,
            SpecimenRepository specimenRepository,
            LabResultRepository labResultRepository,
            BillingFacade billingFacade,
            EncounterFacade encounterFacade,
            PatientFacade patientFacade,
            AuthorizationService authorizationService,
            LabResultViewStateService viewState,
            IdentityFacade identityFacade
    ) {
        this.placeLabOrderService = placeLabOrderService;
        this.labOrderRepository = labOrderRepository;
        this.labTestRepository = labTestRepository;
        this.specimenRepository = specimenRepository;
        this.labResultRepository = labResultRepository;
        this.billingFacade = billingFacade;
        this.encounterFacade = encounterFacade;
        this.patientFacade = patientFacade;
        this.authorizationService = authorizationService;
        this.viewState = viewState;
        this.identityFacade = identityFacade;
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
                request.specialInstructions(), request.urgency(), actor.userId()));
        return LabOrderResponse.from(order);
    }

    // 15-lab-technician.md §3: the Test Queue's single source list. One
    // flat row per open order; the frontend buckets rows into the five
    // sub-tabs (ready to process / in progress / awaiting payment /
    // emergency / overdue) and applies §3.2's sort (emergency, then
    // urgency, then gate_satisfied_at), same "server returns the data,
    // client owns the tab split" pattern as every other QueueShell screen.
    // Unscoped by facility — see LabOrderRepository#findOpenOrders.
    @GetMapping("/queue")
    public List<LabOrderQueueRowResponse> queue(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "LAB_QUEUE_READ")) {
            throw new AccessDeniedException("Missing permission: LAB_QUEUE_READ");
        }
        return labOrderRepository.findOpenOrders().stream()
                .map(this::toQueueRow)
                .sorted(QUEUE_ORDER)
                .toList();
    }

    // §3.2: "ORDER BY (encounter.type = 'EMERGENCY') DESC, urgency ASC,
    // gate_satisfied_at ASC." This codebase has no encounter.type column —
    // the actually-implemented emergency mechanism is an active
    // PaymentBypass (root CLAUDE.md "Billing": IMMEDIATE triage priority
    // creates one automatically), which is exactly what isEmergencyBypass
    // already carries. gate_satisfied_at has no dedicated timestamp either
    // (InvoiceLine.authorizationId has no companion "authorized_at" column)
    // — orderedAt is the closest available proxy and only breaks a tie
    // within the same urgency band, so the approximation doesn't change
    // which row leads.
    private static final Comparator<LabOrderQueueRowResponse> QUEUE_ORDER = Comparator
            .comparing((LabOrderQueueRowResponse row) -> !row.isEmergencyBypass())
            .thenComparing(LabOrderQueueRowResponse::urgency)
            .thenComparing(LabOrderQueueRowResponse::orderedAt);

    private LabOrderQueueRowResponse toQueueRow(LabOrder order) {
        LabTest test = labTestRepository.findById(order.labTestId()).orElse(null);
        PatientIdentity identity = patientFacade.findIdentity(order.patientId()).orElse(null);
        boolean gateSatisfied = billingFacade.isLineAuthorized(order.invoiceLineId());
        boolean emergencyBypass = billingFacade.hasActivePaymentBypass(order.encounterId());

        Specimen latestSpecimen = specimenRepository.findByLabOrderId(order.id()).stream().findFirst().orElse(null);
        boolean overdue = latestSpecimen != null
                && latestSpecimen.status() == SpecimenStatus.IN_PROGRESS
                && test != null
                && Duration.between(latestSpecimen.collectedAt(), Instant.now())
                        .compareTo(Duration.ofHours(test.expectedTatHours())) > 0;

        return new LabOrderQueueRowResponse(
                order.id(), order.encounterId(), order.patientId(),
                identity == null ? null : identity.fullName(), identity == null ? null : identity.hospitalNumber(),
                test == null ? null : test.code(), test == null ? "Unknown test" : test.name(),
                test == null ? null : test.specimenType(), test == null ? null : test.containerType(),
                test == null ? 0 : test.expectedTatHours(),
                order.urgency(), order.orderedAt(), gateSatisfied, emergencyBypass,
                latestSpecimen == null ? null : latestSpecimen.id(),
                latestSpecimen == null ? null : latestSpecimen.accessionNo(),
                latestSpecimen == null ? null : latestSpecimen.status(),
                latestSpecimen == null ? null : latestSpecimen.quality(),
                latestSpecimen == null ? null : latestSpecimen.collectedAt(),
                overdue);
    }

    // 08-doctor-navigation.md §5: "Request History" — every lab request
    // this doctor has raised, with live status.
    @GetMapping("/mine")
    public List<LabOrderHistoryResponse> mine(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "LAB_ORDER_CREATE")) {
            throw new AccessDeniedException("Missing permission: LAB_ORDER_CREATE");
        }
        return labOrderRepository.findByOrderedBy(actor.userId()).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @GetMapping("/mine/unread-result-orders")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permission", content = @io.swagger.v3.oas.annotations.media.Content)
    public List<UUID> unreadResultOrders(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "LAB_ORDER_CREATE"))
            throw new AccessDeniedException("Missing permission: LAB_ORDER_CREATE");
        return viewState.unreadOrderIds(actor.userId());
    }

    @GetMapping("/mine/{id}")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.Operation(summary = "View one of the doctor's laboratory requests", description = "Requires an ordering doctor with a current care relationship. Opening request metadata does not change laboratory processing status.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Request not accessible", content = @io.swagger.v3.oas.annotations.media.Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Request not found", content = @io.swagger.v3.oas.annotations.media.Content)
    public LabOrderDetailResponse ownOrder(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        LabOrder order = labOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + id));
        if (!actor.userId().equals(order.orderedBy()) ||
            !authorizationService.hasPermission(actor, "LAB_ORDER_CREATE", ScopeRef.of(ScopeType.FACILITY, order.facilityId())) ||
            authorizationService.checkPatientAccess(actor, "LAB_RESULT_READ", order.patientId(),
                ScopeRef.of(ScopeType.FACILITY, order.facilityId())) != com.adags.shared.authz.PolicyResult.ALLOWED)
            throw new AccessDeniedException("Cannot view this laboratory request");
        var specimens = specimenRepository.findByLabOrderId(id).stream().map(s ->
                new LabOrderDetailResponse.Sample(s.accessionNo(), s.sampleType(), s.containerType(),
                        s.status().name(), s.collectedAt(), s.quality().name())).toList();
        return new LabOrderDetailResponse(toHistoryResponse(order),
                identityFacade.findUsername(order.orderedBy()).orElse("Unknown doctor"), order.clinicalIndication(),
                order.specialInstructions(), specimens);
    }

    public record LabOrderDetailResponse(LabOrderHistoryResponse order, String requestingDoctorName, String clinicalIndication,
            String specialInstructions, List<Sample> samples) {
        public record Sample(String accessionNo, String sampleType, String containerType,
                             String status, Instant collectedAt, String quality) {}
    }

    // 08-doctor-navigation.md §3.3: every lab order on this encounter,
    // across every ordering doctor — the patient timeline's lab section.
    // Scoped at FACILITY, same granularity as place() above (class comment).
    @GetMapping("/by-encounter/{encounterId}")
    public List<LabOrderHistoryResponse> byEncounter(@PathVariable UUID encounterId, @AuthenticationPrincipal Actor actor) {
        EncounterContext encounterContext = encounterFacade.findContext(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!authorizationService.hasPermission(
                actor, "LAB_RESULT_READ", ScopeRef.of(ScopeType.FACILITY, encounterContext.facilityId()))) {
            throw new AccessDeniedException("Missing permission: LAB_RESULT_READ");
        }
        if (authorizationService.hasPermission(actor, "CONSULTATION_CREATE") &&
            authorizationService.checkPatientAccess(actor, "LAB_RESULT_READ", encounterContext.patientId(),
                ScopeRef.of(ScopeType.FACILITY, encounterContext.facilityId())) != com.adags.shared.authz.PolicyResult.ALLOWED)
            throw new AccessDeniedException("An active care relationship is required for laboratory history.");
        return labOrderRepository.findByEncounterId(encounterId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    // Status derivation per 08-doctor-navigation.md §5.1's vocabulary, from
    // what this phase's data model actually tracks: no LabOrder cancel
    // path exists yet, so CANCELLED/EXPIRED never appear here — an honest
    // gap, not a fabricated status.
    private LabOrderHistoryResponse toHistoryResponse(LabOrder order) {
        LabTest test = labTestRepository.findById(order.labTestId()).orElse(null);
        boolean authorized = billingFacade.isLineAuthorized(order.invoiceLineId());
        String status;
        if (!authorized) {
            status = "PENDING_PAYMENT";
        } else {
            List<Specimen> specimens = specimenRepository.findByLabOrderId(order.id());
            boolean resultReleased = specimens.stream()
                    .flatMap(specimen -> labResultRepository.findBySpecimenId(specimen.id()).stream())
                    .anyMatch(result -> result.releasedAt() != null);
            if (resultReleased) {
                status = "COMPLETED";
            } else if (!specimens.isEmpty()) {
                status = "IN_PROGRESS";
            } else {
                status = "AWAITING_COLLECTION";
            }
        }
        return new LabOrderHistoryResponse(order.id(), order.encounterId(), order.patientId(),
                test == null ? null : test.code(), test == null ? "Unknown test" : test.name(),
                order.orderedBy(), order.orderedAt(), order.urgency(), status);
    }

    public record LabOrderHistoryResponse(
            UUID id, UUID encounterId, UUID patientId, String labTestCode, String labTestName,
            UUID orderedBy, java.time.Instant orderedAt, com.adags.modules.laboratory.domain.Urgency urgency, String status
    ) {
    }
}
