package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.AcknowledgeShiftHandoverService;
import com.adags.modules.inpatient.application.RecordShiftHandoverCommand;
import com.adags.modules.inpatient.application.RecordShiftHandoverService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.ShiftHandover;
import com.adags.modules.inpatient.domain.ShiftHandoverRepository;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 17-ward-nurse.md §7: shift handover, the highest-risk moment in ward
// nursing. SHIFT_HANDOVER_CREATE for the outgoing nurse's record,
// SHIFT_HANDOVER_ACKNOWLEDGE for the incoming nurse's acknowledgement —
// both already seeded and granted to WARD_NURSE/NURSE in V5.
@RestController
@RequestMapping("/api/admissions/{admissionId}/shift-handovers")
public class ShiftHandoverController {

    private final RecordShiftHandoverService recordShiftHandoverService;
    private final AcknowledgeShiftHandoverService acknowledgeShiftHandoverService;
    private final ShiftHandoverRepository shiftHandoverRepository;
    private final AdmissionRepository admissionRepository;
    private final AuthorizationService authorizationService;

    public ShiftHandoverController(
            RecordShiftHandoverService recordShiftHandoverService,
            AcknowledgeShiftHandoverService acknowledgeShiftHandoverService,
            ShiftHandoverRepository shiftHandoverRepository,
            AdmissionRepository admissionRepository,
            AuthorizationService authorizationService
    ) {
        this.recordShiftHandoverService = recordShiftHandoverService;
        this.acknowledgeShiftHandoverService = acknowledgeShiftHandoverService;
        this.shiftHandoverRepository = shiftHandoverRepository;
        this.admissionRepository = admissionRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftHandoverResponse record(
            @PathVariable UUID admissionId, @Valid @RequestBody RecordShiftHandoverRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, admissionId, "SHIFT_HANDOVER_CREATE");
        ShiftHandover handover = recordShiftHandoverService.record(new RecordShiftHandoverCommand(
                admissionId, request.wardId(), request.shiftFrom(), request.shiftTo(), request.situation(),
                request.background(), request.assessment(), request.recommendation(),
                request.additionalOutstandingTasks(), actor.userId()));
        return ShiftHandoverResponse.from(handover);
    }

    @GetMapping
    public List<ShiftHandoverResponse> list(@PathVariable UUID admissionId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, admissionId, "WARD_PATIENT_READ");
        return shiftHandoverRepository.findByAdmissionId(admissionId).stream().map(ShiftHandoverResponse::from).toList();
    }

    @PostMapping("/{id}/acknowledge")
    public void acknowledge(@PathVariable UUID admissionId, @PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, admissionId, "SHIFT_HANDOVER_ACKNOWLEDGE");
        acknowledgeShiftHandoverService.acknowledge(id, actor.userId());
    }

    private void requirePermission(Actor actor, UUID admissionId, String permissionCode) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + admissionId));
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(ScopeType.FACILITY, admission.facilityId()))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
