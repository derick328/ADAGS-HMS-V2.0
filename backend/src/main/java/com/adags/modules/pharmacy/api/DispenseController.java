package com.adags.modules.pharmacy.api;

import com.adags.modules.identity.IdentityFacade;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.patients.PatientFacade;
import com.adags.modules.pharmacy.application.DispenseCommand;
import com.adags.modules.pharmacy.application.DispenseContextService;
import com.adags.modules.pharmacy.application.DispenseLabelService;
import com.adags.modules.pharmacy.application.DispenseLineInput;
import com.adags.modules.pharmacy.application.DispenseResult;
import com.adags.modules.pharmacy.application.DispenseService;
import com.adags.modules.pharmacy.domain.Dispense;
import com.adags.modules.pharmacy.domain.DispenseRepository;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.ItemSummary;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §5: THE GATE AT DISPENSING — DispenseService calls
// BillingFacade.authorizeConsumption before anything is persisted; an
// unsatisfied gate throws BillingGateRejectedException, which
// GlobalExceptionHandler already turns into a 402 (backend/CLAUDE.md
// "API"), same treatment SpecimenController documents for lab.
@RestController
public class DispenseController {

    private final DispenseService dispenseService;
    private final DispenseContextService dispenseContextService;
    private final DispenseLabelService dispenseLabelService;
    private final DispenseRepository dispenseRepository;
    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final InventoryFacade inventoryFacade;
    private final PatientFacade patientFacade;
    private final IdentityFacade identityFacade;
    private final AuthorizationService authorizationService;

    public DispenseController(
            DispenseService dispenseService,
            DispenseContextService dispenseContextService,
            DispenseLabelService dispenseLabelService,
            DispenseRepository dispenseRepository,
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            InventoryFacade inventoryFacade,
            PatientFacade patientFacade,
            IdentityFacade identityFacade,
            AuthorizationService authorizationService
    ) {
        this.dispenseService = dispenseService;
        this.dispenseContextService = dispenseContextService;
        this.dispenseLabelService = dispenseLabelService;
        this.dispenseRepository = dispenseRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.inventoryFacade = inventoryFacade;
        this.patientFacade = patientFacade;
        this.identityFacade = identityFacade;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/prescription-lines/{id}/dispense-context")
    public DispenseContextResponse dispenseContext(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermissionForLine(actor, "PRESCRIPTION_QUEUE_READ", id);
        return DispenseContextResponse.from(dispenseContextService.build(id));
    }

    @PostMapping("/api/dispenses")
    @ResponseStatus(HttpStatus.CREATED)
    public DispenseResponse dispense(@Valid @RequestBody DispenseRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermissionForLine(actor, "DISPENSE_CREATE", request.prescriptionLineId());

        DispenseResult result = dispenseService.dispense(new DispenseCommand(
                request.prescriptionLineId(),
                request.lines().stream()
                        .map(l -> new DispenseLineInput(l.batchId(), l.quantity(), l.overrideReason()))
                        .toList(),
                actor.userId(), request.idempotencyKey(), request.counsellingNotes(), request.dispensingNotes()));
        return DispenseResponse.from(result);
    }

    // 12-pharmacist.md §4.6: the label payload — rendering is the frontend's job.
    @GetMapping("/api/dispenses/{id}/label")
    public DispenseLabelResponse label(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Dispense dispense = dispenseRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Dispense not found: " + id));
        requirePermissionForLine(actor, "DISPENSE_HISTORY_READ", dispense.prescriptionLineId());
        return DispenseLabelResponse.from(dispenseLabelService.generate(id));
    }

    // 08-doctor-navigation.md §5: doctor's Request History detail — the
    // dispense trail for one prescription line. Gated on PRESCRIPTION_READ
    // (which DOCTOR holds; pharmacist's DISPENSE_HISTORY_READ also
    // suffices via requirePermissionForLine's normal permission match) so
    // the doctor can see how their prescribed drug was actually
    // dispensed (batch, quantity, pharmacist, counselling notes) without
    // being granted the pharmacy-side history permission.
    @GetMapping("/api/dispenses/by-prescription-line/{prescriptionLineId}")
    public List<DispenseHistoryResponse> byPrescriptionLine(
            @PathVariable UUID prescriptionLineId, @AuthenticationPrincipal Actor actor
    ) {
        requirePermissionForLine(actor, "PRESCRIPTION_READ", prescriptionLineId);
        return dispenseRepository.findByPrescriptionLineId(prescriptionLineId).stream()
                .sorted(Comparator.comparing(Dispense::dispensedAt).reversed())
                .map(this::toHistoryResponse)
                .toList();
    }

    // 12-pharmacist.md §11: "My dispenses" — this pharmacist's own work,
    // the History screen's default tab. Unscoped (no facility check) —
    // same treatment as PrescriptionController#mine, matching what the
    // permission itself already limits the result to (this actor's own
    // dispensedBy rows).
    @GetMapping("/api/dispenses/mine")
    public List<DispenseHistoryResponse> mine(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "DISPENSE_HISTORY_READ")) {
            throw new AccessDeniedException("Missing permission: DISPENSE_HISTORY_READ");
        }
        return dispenseRepository.findByDispensedBy(actor.userId()).stream().map(this::toHistoryResponse).toList();
    }

    // 12-pharmacist.md §11: "Patient lookup" — search by patient, every
    // dispense regardless of recording pharmacist. This module cannot
    // search patients directly (root CLAUDE.md "Modules": no cross-module
    // entity imports, and pharmacy holds no PATIENT_SEARCH permission —
    // only PATIENT_DISPENSE_LOOKUP), so the candidate set is every
    // patient this module has ever dispensed to, name/hospital-number
    // filtered in application code via PatientFacade.findIdentity.
    @GetMapping("/api/dispenses/patient-search")
    public List<DispenseHistoryResponse> patientSearch(@RequestParam String q, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "PATIENT_DISPENSE_LOOKUP")) {
            throw new AccessDeniedException("Missing permission: PATIENT_DISPENSE_LOOKUP");
        }
        String needle = q.trim().toLowerCase();
        if (needle.isEmpty()) {
            return List.of();
        }
        return dispenseRepository.findDistinctDispensedPatientIds().stream()
                .filter(patientId -> matchesPatient(patientId, needle))
                .flatMap(patientId -> dispenseRepository.findByPatientId(patientId).stream())
                .map(this::toHistoryResponse)
                .sorted(Comparator.comparing(DispenseHistoryResponse::dispensedAt).reversed())
                .toList();
    }

    private boolean matchesPatient(UUID patientId, String needle) {
        PatientIdentity identity = patientFacade.findIdentity(patientId).orElse(null);
        if (identity == null) {
            return false;
        }
        return identity.fullName().toLowerCase().contains(needle)
                || identity.hospitalNumber().toLowerCase().contains(needle);
    }

    private DispenseHistoryResponse toHistoryResponse(Dispense dispense) {
        PrescriptionLine line = prescriptionLineRepository.findById(dispense.prescriptionLineId())
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + dispense.prescriptionLineId()));
        PrescriptionOrder order = prescriptionOrderRepository.findById(line.prescriptionOrderId())
                .orElseThrow(() -> new NoSuchElementException("Prescription order not found: " + line.prescriptionOrderId()));
        ItemSummary item = inventoryFacade.findItemSummary(line.itemId()).orElse(null);
        PatientIdentity identity = patientFacade.findIdentity(order.patientId()).orElse(null);
        String dispensedByUsername = identityFacade.findUsername(dispense.dispensedBy()).orElse(null);

        return new DispenseHistoryResponse(
                dispense.id(), line.id(), order.id(), order.encounterId(), order.patientId(),
                identity == null ? null : identity.fullName(), identity == null ? null : identity.hospitalNumber(),
                item == null ? null : item.code(), item == null ? "Unknown item" : item.name(),
                dispense.quantityDispensed(), dispense.dispensedAt(), dispense.dispensedBy(), dispensedByUsername,
                dispense.counsellingNotes(), dispense.dispensingNotes());
    }

    private void requirePermissionForLine(Actor actor, String permissionCode, UUID prescriptionLineId) {
        PrescriptionLine line = prescriptionLineRepository.findById(prescriptionLineId)
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + prescriptionLineId));
        PrescriptionOrder order = prescriptionOrderRepository.findById(line.prescriptionOrderId())
                .orElseThrow(() -> new NoSuchElementException("Prescription order not found: " + line.prescriptionOrderId()));
        if (!authorizationService.hasPermission(
                actor, permissionCode, ScopeRef.of(ScopeType.FACILITY, order.facilityId()))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
