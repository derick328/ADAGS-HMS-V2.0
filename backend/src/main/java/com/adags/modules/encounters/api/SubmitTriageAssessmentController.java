package com.adags.modules.encounters.api;

import com.adags.modules.encounters.application.SubmitTriageAssessmentCommand;
import com.adags.modules.encounters.application.SubmitTriageAssessmentService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.TriageAssessment;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;

// 06-triage.md §6: the one transaction, exposed. Was previously unreachable
// from outside the JVM — SubmitTriageAssessmentService existed with full
// test coverage but no controller wrapped it. This is that controller;
// none of the submission logic changes.
@RestController
public class SubmitTriageAssessmentController {

    private final SubmitTriageAssessmentService submitTriageAssessmentService;
    private final EncounterRepository encounterRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    public SubmitTriageAssessmentController(
            SubmitTriageAssessmentService submitTriageAssessmentService,
            EncounterRepository encounterRepository,
            AuthorizationService authorizationService,
            Clock clock
    ) {
        this.submitTriageAssessmentService = submitTriageAssessmentService;
        this.encounterRepository = encounterRepository;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    @PostMapping("/api/encounters/{encounterId}/triage-assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public TriageAssessmentResponse submit(
            @PathVariable UUID encounterId,
            @Valid @RequestBody SubmitTriageAssessmentRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!(encounter.departmentId() == null
                ? authorizationService.hasPermissionForPendingEncounter(actor, "TRIAGE_ASSESS", encounter.facilityId())
                : authorizationService.hasPermission(actor, "TRIAGE_ASSESS", encounter.scope()))) {
            throw new AccessDeniedException("Missing permission: TRIAGE_ASSESS");
        }

        SubmitTriageAssessmentCommand command = new SubmitTriageAssessmentCommand(
                encounterId, request.expectedEncounterVersion(), request.assignedProviderId(),
                request.chiefComplaint(), request.currentSymptoms(), request.allergies(), request.comorbidities(),
                request.modeOfAmbulation(), request.hasPain(), request.painScore(), request.painLocation(),
                request.fallRisk(), request.fallScore(), request.infectionRisk(),
                request.pregnancyStatus(), request.gestationalWeeks(),
                request.priority(), request.immediateReason(), request.immediateReasonOtherText(),
                request.notes(), request.supersedesId(),
                request.temperatureC(), request.systolicMmHg(), request.diastolicMmHg(), request.pulseBpm(),
                request.respiratoryRate(), request.spo2Pct(), request.weightKg(), request.heightCm(),
                request.avpu(), request.bloodGlucoseMmol(),
                actor.userId(), clock.instant());

        TriageAssessment saved = submitTriageAssessmentService.submit(command);
        return TriageAssessmentResponse.from(saved);
    }
}
