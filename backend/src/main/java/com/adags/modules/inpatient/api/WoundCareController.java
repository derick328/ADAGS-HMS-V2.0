package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.RecordWoundCareCommand;
import com.adags.modules.inpatient.application.RecordWoundCareService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.WoundCare;
import com.adags.modules.inpatient.domain.WoundCareRepository;
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

@RestController
@RequestMapping("/api/admissions/{admissionId}/wound-care")
public class WoundCareController {

    private final RecordWoundCareService recordWoundCareService;
    private final WoundCareRepository woundCareRepository;
    private final AdmissionRepository admissionRepository;
    private final AuthorizationService authorizationService;

    public WoundCareController(
            RecordWoundCareService recordWoundCareService,
            WoundCareRepository woundCareRepository,
            AdmissionRepository admissionRepository,
            AuthorizationService authorizationService
    ) {
        this.recordWoundCareService = recordWoundCareService;
        this.woundCareRepository = woundCareRepository;
        this.admissionRepository = admissionRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WoundCareResponse record(
            @PathVariable UUID admissionId, @Valid @RequestBody RecordWoundCareRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, admissionId, "WOUND_CARE_RECORD");
        WoundCare saved = recordWoundCareService.record(new RecordWoundCareCommand(
                admissionId, request.appearance(), request.dressingChanged(), request.dressingType(),
                request.signsOfInfection(), request.infectionDescription(), request.remarks(), actor.userId()));
        return WoundCareResponse.from(saved);
    }

    @GetMapping
    public List<WoundCareResponse> list(@PathVariable UUID admissionId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, admissionId, "WARD_PATIENT_READ");
        return woundCareRepository.findByAdmissionId(admissionId).stream().map(WoundCareResponse::from).toList();
    }

    private void requirePermission(Actor actor, UUID admissionId, String permissionCode) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + admissionId));
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(ScopeType.FACILITY, admission.facilityId()))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
