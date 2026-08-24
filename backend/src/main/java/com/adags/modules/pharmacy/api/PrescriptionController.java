package com.adags.modules.pharmacy.api;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inventory.InventoryFacade;
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
    private final AuthorizationService authorizationService;

    public PrescriptionController(
            PlacePrescriptionService placePrescriptionService,
            PrescriptionOrderRepository prescriptionOrderRepository,
            PrescriptionLineRepository prescriptionLineRepository,
            InventoryFacade inventoryFacade,
            BillingFacade billingFacade,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.placePrescriptionService = placePrescriptionService;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.inventoryFacade = inventoryFacade;
        this.billingFacade = billingFacade;
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
                request.substitutionAllowed(), actor.userId(), request.route(), request.doseAmount(),
                request.doseUnit(), request.frequency(), request.durationDays()));
        return PrescriptionLineResponse.from(line);
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
                            line.quantityPrescribed(), order.prescribedBy(), order.prescribedAt(), status);
                })
                .toList();
    }

    public record PrescriptionHistoryResponse(
            UUID prescriptionLineId, UUID prescriptionOrderId, UUID encounterId, UUID patientId,
            String itemCode, String itemName, int quantityPrescribed,
            UUID prescribedBy, java.time.Instant prescribedAt, String status
    ) {
    }
}
