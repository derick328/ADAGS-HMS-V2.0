package com.adags.modules.surgery.api;

import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
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

// 17-ward-nurse.md §10: "Post-op observations write to VitalSigns
// (context = POST_OP), not separate fields." Same SURGERY_EPISODE_DOCUMENT
// permission as the rest of the episode's nursing documentation.
@RestController
@RequestMapping("/api/surgery-orders/{id}/vitals")
public class SurgeryVitalsController {

    private final ClinicalFacade clinicalFacade;
    private final SurgeryOrderRepository surgeryOrderRepository;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public SurgeryVitalsController(
            ClinicalFacade clinicalFacade,
            SurgeryOrderRepository surgeryOrderRepository,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService
    ) {
        this.clinicalFacade = clinicalFacade;
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UUID record(
            @PathVariable UUID id, @Valid @RequestBody RecordSurgeryVitalsRequest request, @AuthenticationPrincipal Actor actor
    ) {
        SurgeryOrder order = surgeryOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + id));
        EncounterContext context = encounterFacade.findContext(order.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + order.encounterId()));
        if (!authorizationService.hasPermission(
                actor, "SURGERY_EPISODE_DOCUMENT", context.scope())) {
            throw new AccessDeniedException("Missing permission: SURGERY_EPISODE_DOCUMENT");
        }

        return clinicalFacade.recordPostOpVitalSigns(
                order.encounterId(), context.patientId(), id,
                request.temperatureC(), request.systolicMmHg(), request.diastolicMmHg(),
                request.pulseBpm(), request.respiratoryRate(), request.spo2Pct(),
                request.weightKg(), request.heightCm(), request.avpu(), request.bloodGlucoseMmol(),
                actor.userId(), request.occurredAt());
    }
}
