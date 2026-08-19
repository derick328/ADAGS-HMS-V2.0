package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.RecordWardVitalsCommand;
import com.adags.modules.inpatient.application.RecordWardVitalsService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

// 17-ward-nurse.md §4: ward rounds vitals against an admission —
// context = WARD_ROUNDS in the same VitalSigns table triage/consultation
// share. A breaching reading raises a ward alert and suggests CRITICAL
// inside RecordWardVitalsService — nothing further to do here.
@RestController
@RequestMapping("/api/admissions/{admissionId}/vitals")
public class WardVitalsController {

    private final RecordWardVitalsService recordWardVitalsService;
    private final AdmissionRepository admissionRepository;
    private final AuthorizationService authorizationService;

    public WardVitalsController(
            RecordWardVitalsService recordWardVitalsService,
            AdmissionRepository admissionRepository,
            AuthorizationService authorizationService
    ) {
        this.recordWardVitalsService = recordWardVitalsService;
        this.admissionRepository = admissionRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UUID record(
            @PathVariable UUID admissionId, @Valid @RequestBody RecordWardVitalsRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + admissionId));
        if (!authorizationService.hasPermission(
                actor, "VITALS_RECORD", ScopeRef.of(ScopeType.FACILITY, admission.facilityId()))) {
            throw new AccessDeniedException("Missing permission: VITALS_RECORD");
        }

        return recordWardVitalsService.record(new RecordWardVitalsCommand(
                admissionId, request.temperatureC(), request.systolicMmHg(), request.diastolicMmHg(),
                request.pulseBpm(), request.respiratoryRate(), request.spo2Pct(), request.weightKg(),
                request.heightCm(), request.avpu(), request.bloodGlucoseMmol(), request.painScore(),
                request.intakeMl(), request.outputMl(), actor.userId(), request.occurredAt()));
    }
}
