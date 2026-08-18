package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.application.MarkUnavailableService;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §5.2.
@RestController
@RequestMapping("/api/prescription-lines")
public class PrescriptionLineController {

    private final MarkUnavailableService markUnavailableService;
    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final AuthorizationService authorizationService;

    public PrescriptionLineController(
            MarkUnavailableService markUnavailableService,
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            AuthorizationService authorizationService
    ) {
        this.markUnavailableService = markUnavailableService;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/{id}/mark-unavailable")
    public PrescriptionLineResponse markUnavailable(
            @PathVariable UUID id, @Valid @RequestBody MarkUnavailableRequest request, @AuthenticationPrincipal Actor actor
    ) {
        PrescriptionLine line = prescriptionLineRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + id));
        PrescriptionOrder order = prescriptionOrderRepository.findById(line.prescriptionOrderId())
                .orElseThrow(() -> new NoSuchElementException("Prescription order not found: " + line.prescriptionOrderId()));
        if (!authorizationService.hasPermission(
                actor, "PRESCRIPTION_MARK_UNAVAILABLE", ScopeRef.of(ScopeType.FACILITY, order.facilityId()))) {
            throw new AccessDeniedException("Missing permission: PRESCRIPTION_MARK_UNAVAILABLE");
        }

        return PrescriptionLineResponse.from(markUnavailableService.markUnavailable(id, request.reason(), actor.userId()));
    }
}
