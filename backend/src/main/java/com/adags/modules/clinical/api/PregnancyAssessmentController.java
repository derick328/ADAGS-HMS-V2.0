package com.adags.modules.clinical.api;

import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.clinical.application.RecordPregnancyAssessmentService;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/encounters/{encounterId}/pregnancy-assessment")
public class PregnancyAssessmentController {
    public record AssessmentResponse(boolean applicable, String status) {}
    public record AssessmentRequest(@NotNull Status status) {}
    public enum Status { YES, NO, UNKNOWN }

    private final EncounterFacade encounters;
    private final ClinicalFacade clinical;
    private final RecordPregnancyAssessmentService assessments;
    private final ConsultationRecordRepository consultationRecords;
    private final AuthorizationService authorization;

    public PregnancyAssessmentController(EncounterFacade encounters, ClinicalFacade clinical,
            RecordPregnancyAssessmentService assessments, ConsultationRecordRepository consultationRecords,
            AuthorizationService authorization) {
        this.encounters = encounters;
        this.clinical = clinical;
        this.assessments = assessments;
        this.consultationRecords = consultationRecords;
        this.authorization = authorization;
    }

    @GetMapping
    public AssessmentResponse get(@PathVariable UUID encounterId, @AuthenticationPrincipal Actor actor) {
        EncounterContext context = context(encounterId);
        require(actor, "CLINICAL_NOTE_READ", context.facilityId());
        return response(encounterId, context);
    }

    @PostMapping
    public AssessmentResponse record(@PathVariable UUID encounterId, @Valid @RequestBody AssessmentRequest request,
            @AuthenticationPrincipal Actor actor) {
        EncounterContext context = context(encounterId);
        require(actor, "CONSULTATION_CREATE", context.facilityId());
        if (consultationRecords.findOpenByEncounterAndAuthor(encounterId, actor.userId()).isEmpty()) {
            throw new AccessDeniedException("Open this consultation before recording pregnancy status.");
        }
        if (!clinical.isPregnancyAssessmentApplicable(context.patientId())) {
            throw new IllegalArgumentException("Pregnancy assessment does not apply to this patient.");
        }
        assessments.record(encounterId, context.patientId(), request.status().name(), actor.userId());
        return response(encounterId, context);
    }

    private AssessmentResponse response(UUID encounterId, EncounterContext context) {
        boolean applicable = clinical.isPregnancyAssessmentApplicable(context.patientId());
        return new AssessmentResponse(applicable, applicable ? clinical.findPregnancyStatus(encounterId).orElse(null) : null);
    }

    private EncounterContext context(UUID encounterId) {
        return encounters.findContext(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
    }

    private void require(Actor actor, String permission, UUID facilityId) {
        if (!authorization.hasPermission(actor, permission, ScopeRef.of(ScopeType.FACILITY, facilityId))) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
    }
}
