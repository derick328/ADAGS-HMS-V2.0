package com.adags.modules.pharmacy.api;

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
    private final AuthorizationService authorizationService;

    public DispenseController(
            DispenseService dispenseService,
            DispenseContextService dispenseContextService,
            DispenseLabelService dispenseLabelService,
            DispenseRepository dispenseRepository,
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            AuthorizationService authorizationService
    ) {
        this.dispenseService = dispenseService;
        this.dispenseContextService = dispenseContextService;
        this.dispenseLabelService = dispenseLabelService;
        this.dispenseRepository = dispenseRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
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
