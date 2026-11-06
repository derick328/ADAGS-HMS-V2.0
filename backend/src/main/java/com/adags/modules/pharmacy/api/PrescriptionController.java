package com.adags.modules.pharmacy.api;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.InpatientFacade;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.patients.PatientFacade;
import com.adags.modules.pharmacy.application.PlacePrescriptionCommand;
import com.adags.modules.pharmacy.application.PlacePrescriptionService;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.ItemSummary;
import com.adags.shared.kernel.PatientIdentity;
import com.adags.shared.kernel.WardSummary;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

// D1 (12-pharmacist.md §1): PRESCRIPTION_CREATE — doctors only. No
// substitution, no doctor UI beyond this one action; see
// PlacePrescriptionService's comment for why this exists at all inside a
// build brief about dispensing.
@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PlacePrescriptionService placePrescriptionService;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final PrescriptionLineRepository prescriptionLineRepository;
    private final InventoryFacade inventoryFacade;
    private final BillingFacade billingFacade;
    private final EncounterFacade encounterFacade;
    private final InpatientFacade inpatientFacade;
    private final PatientFacade patientFacade;
    private final AuthorizationService authorizationService;
    private final com.adags.shared.authz.AuditRecorder historyAudit;

    public PrescriptionController(
            PlacePrescriptionService placePrescriptionService,
            PrescriptionOrderRepository prescriptionOrderRepository,
            PrescriptionLineRepository prescriptionLineRepository,
            InventoryFacade inventoryFacade,
            BillingFacade billingFacade,
            EncounterFacade encounterFacade,
            InpatientFacade inpatientFacade,
            PatientFacade patientFacade,
            AuthorizationService authorizationService,
            com.adags.shared.authz.AuditRecorder historyAudit
    ) {
        this.placePrescriptionService = placePrescriptionService;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.inventoryFacade = inventoryFacade;
        this.billingFacade = billingFacade;
        this.encounterFacade = encounterFacade;
        this.inpatientFacade = inpatientFacade;
        this.patientFacade = patientFacade;
        this.authorizationService = authorizationService;
        this.historyAudit = historyAudit;
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
                request.substitutionAllowed(), actor.userId(), request.route(), request.doseAmount(),
                request.doseUnit(), request.frequency(), request.durationDays(),
                request.requestedQuantity(), request.requestedUnit()));
        return PrescriptionLineResponse.from(line);
    }

    // 12-pharmacist.md §4: the Dashboard's / Dispense Queue's single source
    // list. One flat row per open line; the frontend buckets into the six
    // queues (ready / partially payable / awaiting payment / emergency /
    // inpatient — surgery items deferred with the surgery module) and
    // sorts, same "server returns the data, client owns the tab split"
    // pattern LabOrderController's /queue documents. Unscoped by facility,
    // same reasoning as LabOrderController#queue (no multi-facility
    // pharmacist role exists yet to distinguish).
    @GetMapping("/queue")
    public List<PrescriptionQueueRowResponse> queue(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "PRESCRIPTION_QUEUE_READ")) {
            throw new AccessDeniedException("Missing permission: PRESCRIPTION_QUEUE_READ");
        }
        List<PrescriptionLine> openLines = prescriptionLineRepository.findOpen();
        // Batches by order so each PrescriptionOrder (and the patient
        // identity it points at) is only resolved once, not once per line;
        // ward is resolved once per encounter for the same reason.
        Map<UUID, PrescriptionOrder> ordersById = new HashMap<>();
        Map<UUID, Optional<WardSummary>> wardByEncounter = new HashMap<>();
        return openLines.stream()
                .map(line -> {
                    PrescriptionOrder order = ordersById.computeIfAbsent(line.prescriptionOrderId(),
                            id -> prescriptionOrderRepository.findById(id)
                                    .orElseThrow(() -> new NoSuchElementException("Prescription order not found: " + id)));
                    Optional<WardSummary> ward = wardByEncounter.computeIfAbsent(
                            order.encounterId(), inpatientFacade::findActiveAdmissionWard);
                    return toQueueRow(line, order, ward);
                })
                .toList();
    }

    private PrescriptionQueueRowResponse toQueueRow(
            PrescriptionLine line, PrescriptionOrder order, Optional<WardSummary> ward) {
        ItemSummary item = inventoryFacade.findItemSummary(line.itemId()).orElse(null);
        PatientIdentity identity = patientFacade.findIdentity(order.patientId()).orElse(null);
        boolean gateSatisfied = billingFacade.isLineAuthorized(line.invoiceLineId());
        boolean emergencyBypass = billingFacade.hasActivePaymentBypass(order.encounterId());
        boolean isInpatient = inpatientFacade.findActiveAdmissionId(order.encounterId()).isPresent();

        return new PrescriptionQueueRowResponse(
                line.id(), order.id(), order.encounterId(), order.patientId(),
                identity == null ? null : identity.fullName(), identity == null ? null : identity.hospitalNumber(),
                line.itemId(), item == null ? null : item.code(), item == null ? "Unknown item" : item.name(),
                line.quantityPrescribed(), line.quantityDispensed(), line.remaining(), line.dosageInstructions(),
                line.status(), order.prescribedAt(), gateSatisfied, emergencyBypass, isInpatient,
                item == null ? null : item.classification(), item == null ? null : item.form(),
                ward.map(WardSummary::wardId).orElse(null), ward.map(WardSummary::name).orElse(null));
    }

    // 08-doctor-navigation.md §5: "Request History" — every prescription
    // line this doctor has raised, with live status. One row per line
    // (rather than one per order aggregating several items) — a line is
    // this codebase's actual gate/dispense unit (root CLAUDE.md "Billing":
    // "gating is per invoice line, never per invoice").
    @GetMapping("/mine")
    public List<PrescriptionHistoryResponse> mine(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "PRESCRIPTION_READ")) {
            throw new AccessDeniedException("Missing permission: PRESCRIPTION_READ");
        }
        return prescriptionOrderRepository.findByPrescribedBy(actor.userId()).stream()
                .flatMap(order -> toHistoryResponses(order).stream())
                .toList();
    }

    // 08-doctor-navigation.md §3.3: every prescription line on this
    // encounter, across every prescriber — the patient timeline's
    // prescription section. Scoped at FACILITY, same granularity as
    // place() above.
    @GetMapping("/by-encounter/{encounterId}")
    public List<PrescriptionHistoryResponse> byEncounter(@PathVariable UUID encounterId, @AuthenticationPrincipal Actor actor) {
        EncounterContext encounterContext = encounterFacade.findContext(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!authorizationService.hasPermission(
                actor, "PRESCRIPTION_READ", ScopeRef.of(ScopeType.FACILITY, encounterContext.facilityId()))) {
            throw new AccessDeniedException("Missing permission: PRESCRIPTION_READ");
        }
        if (authorizationService.hasPermission(actor, "CONSULTATION_CREATE") &&
                authorizationService.checkPatientAccess(actor, "PRESCRIPTION_READ", encounterContext.patientId(),
                    ScopeRef.of(ScopeType.FACILITY, encounterContext.facilityId())) != com.adags.shared.authz.PolicyResult.ALLOWED)
            throw new AccessDeniedException("An active care relationship is required for clinical history.");
        historyAudit.record(actor.userId(), "PRESCRIPTION_HISTORY_READ", "encounter", encounterId, encounterContext.patientId());
        return prescriptionOrderRepository.findByEncounterId(encounterId).stream()
                .flatMap(order -> toHistoryResponses(order).stream())
                .toList();
    }

    private List<PrescriptionHistoryResponse> toHistoryResponses(PrescriptionOrder order) {
        return prescriptionLineRepository.findByPrescriptionOrderId(order.id()).stream()
                .map(line -> {
                    ItemSummary item = inventoryFacade.findItemSummary(line.itemId()).orElse(null);
                    boolean authorized = billingFacade.isLineAuthorized(line.invoiceLineId());
                    String status;
                    if (!authorized) {
                        status = "PENDING_PAYMENT";
                    } else {
                        status = switch (line.status()) {
                            case AWAITING_DISPENSE -> "AWAITING_COLLECTION";
                            case PARTIALLY_DISPENSED -> "IN_PROGRESS";
                            case COMPLETED -> "COMPLETED";
                            case CANCELLED, UNAVAILABLE -> "CANCELLED";
                        };
                    }
                    return new PrescriptionHistoryResponse(line.id(), order.id(), order.encounterId(), order.patientId(),
                            item == null ? null : item.code(), item == null ? "Unknown item" : item.name(),
                            line.quantityPrescribed(), order.prescribedBy(), order.prescribedAt(), status, line.doseAmount(), line.doseUnit(), line.route(), line.frequency(), line.durationDays(), line.quantityDispensed(), line.dosageInstructions());
                })
                .toList();
    }

    public record PrescriptionHistoryResponse(
            UUID prescriptionLineId, UUID prescriptionOrderId, UUID encounterId, UUID patientId,
            String itemCode, String itemName, int quantityPrescribed,
            UUID prescribedBy, java.time.Instant prescribedAt, String status, Double doseAmount, String doseUnit, String route, com.adags.modules.pharmacy.domain.PrescriptionFrequency frequency, Integer durationDays, int quantityDispensed, String dosageInstructions
    ) {
    }
}
