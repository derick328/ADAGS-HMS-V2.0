package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.AcknowledgeWardAlertService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
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

// 17-ward-nurse.md §4.3: "acknowledgement tracked." Gated on
// WARD_PATIENT_READ — the same permission that lets a nurse or doctor see
// the admission in the first place, mirroring
// CriticalResultNotificationController's LAB_RESULT_READ precedent (no
// dedicated "acknowledge" permission exists in V5's catalogue).
@RestController
@RequestMapping("/api/admissions/{admissionId}/alerts")
public class WardAlertNotificationController {

    private final AcknowledgeWardAlertService acknowledgeWardAlertService;
    private final WardAlertNotificationRepository wardAlertNotificationRepository;
    private final AdmissionRepository admissionRepository;
    private final AuthorizationService authorizationService;

    public WardAlertNotificationController(
            AcknowledgeWardAlertService acknowledgeWardAlertService,
            WardAlertNotificationRepository wardAlertNotificationRepository,
            AdmissionRepository admissionRepository,
            AuthorizationService authorizationService
    ) {
        this.acknowledgeWardAlertService = acknowledgeWardAlertService;
        this.wardAlertNotificationRepository = wardAlertNotificationRepository;
        this.admissionRepository = admissionRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<WardAlertNotification> list(@PathVariable UUID admissionId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, admissionId);
        return wardAlertNotificationRepository.findByAdmissionId(admissionId);
    }

    @PostMapping("/{id}/acknowledge")
    public void acknowledge(@PathVariable UUID admissionId, @PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, admissionId);
        acknowledgeWardAlertService.acknowledge(id, actor.userId());
    }

    private void requirePermission(Actor actor, UUID admissionId) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + admissionId));
        if (!authorizationService.hasPermission(
                actor, "WARD_PATIENT_READ", ScopeRef.of(ScopeType.FACILITY, admission.facilityId()))) {
            throw new AccessDeniedException("Missing permission: WARD_PATIENT_READ");
        }
    }
}
