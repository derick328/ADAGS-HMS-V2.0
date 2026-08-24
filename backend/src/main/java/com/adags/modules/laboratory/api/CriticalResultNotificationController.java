package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.AcknowledgeCriticalResultService;
import com.adags.modules.laboratory.domain.CriticalResultNotification;
import com.adags.modules.laboratory.domain.CriticalResultNotificationRepository;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 15-lab-technician.md §7: "acknowledgement required and tracked." Gated
// on LAB_RESULT_READ — the same permission that lets a doctor see the
// result in the first place (no dedicated "acknowledge" permission exists
// in V5's catalogue).
@RestController
@RequestMapping("/api/critical-result-notifications")
public class CriticalResultNotificationController {

    private final AcknowledgeCriticalResultService acknowledgeCriticalResultService;
    private final CriticalResultNotificationRepository criticalResultNotificationRepository;
    private final LabResultRepository labResultRepository;
    private final LabOrderRepository labOrderRepository;
    private final AuthorizationService authorizationService;

    public CriticalResultNotificationController(
            AcknowledgeCriticalResultService acknowledgeCriticalResultService,
            CriticalResultNotificationRepository criticalResultNotificationRepository,
            LabResultRepository labResultRepository,
            LabOrderRepository labOrderRepository,
            AuthorizationService authorizationService
    ) {
        this.acknowledgeCriticalResultService = acknowledgeCriticalResultService;
        this.criticalResultNotificationRepository = criticalResultNotificationRepository;
        this.labResultRepository = labResultRepository;
        this.labOrderRepository = labOrderRepository;
        this.authorizationService = authorizationService;
    }

    // 15-lab-technician.md §7: "tech's screen shows acknowledgement status
    // so they know it landed" — polled right after a result carrying a
    // critical value is submitted. Gated the same as byEncounter/other
    // read paths in this module — LAB_RESULT_READ at the order's facility.
    @GetMapping("/by-result/{resultId}")
    public List<CriticalResultNotificationResponse> byResult(@PathVariable UUID resultId, @AuthenticationPrincipal Actor actor) {
        LabResult result = labResultRepository.findById(resultId)
                .orElseThrow(() -> new NoSuchElementException("Lab result not found: " + resultId));
        LabOrder order = labOrderRepository.findById(result.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + result.labOrderId()));
        if (!authorizationService.hasPermission(
                actor, "LAB_RESULT_READ", ScopeRef.of(ScopeType.FACILITY, order.facilityId()))) {
            throw new AccessDeniedException("Missing permission: LAB_RESULT_READ");
        }
        return criticalResultNotificationRepository.findByResultId(resultId).stream()
                .map(CriticalResultNotificationResponse::from)
                .toList();
    }

    @PostMapping("/{id}/acknowledge")
    public void acknowledge(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        CriticalResultNotification notification = criticalResultNotificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Critical result notification not found: " + id));
        LabResult result = labResultRepository.findById(notification.resultId())
                .orElseThrow(() -> new NoSuchElementException("Lab result not found: " + notification.resultId()));
        LabOrder order = labOrderRepository.findById(result.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + result.labOrderId()));

        if (!authorizationService.hasPermission(
                actor, "LAB_RESULT_READ", ScopeRef.of(ScopeType.FACILITY, order.facilityId()))) {
            throw new AccessDeniedException("Missing permission: LAB_RESULT_READ");
        }

        acknowledgeCriticalResultService.acknowledge(id, actor.userId());
    }
}
