package com.adags.modules.clinical.api;

import com.adags.modules.clinical.application.RecordVitalSignsCommand;
import com.adags.modules.clinical.application.RecordVitalSignsService;
import com.adags.modules.clinical.domain.Avpu;
import com.adags.modules.clinical.domain.RecordedVital;
import com.adags.modules.clinical.domain.VitalSigns;
import com.adags.modules.clinical.domain.VitalSignsContext;
import com.adags.modules.clinical.domain.VitalSignsRepository;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 07-doctor.md §5.1: the consultation screen's read-only left panel needs
// the triage-recorded vitals — no controller exposed VitalSignsRepository
// anywhere before this (only WardVitalsController's write side existed).
// Read-only, every context (TRIAGE/WARD_ROUNDS/...), newest first — the
// caller filters to what it needs (the doctor's panel wants the latest
// TRIAGE reading; a later ward trend view would want the full list).
@RestController
public class VitalSignsController {

    private final VitalSignsRepository vitalSignsRepository;
    private final RecordVitalSignsService recordVitalSignsService;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public VitalSignsController(
            VitalSignsRepository vitalSignsRepository, RecordVitalSignsService recordVitalSignsService,
            EncounterFacade encounterFacade, AuthorizationService authorizationService
    ) {
        this.vitalSignsRepository = vitalSignsRepository;
        this.recordVitalSignsService = recordVitalSignsService;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
    }

    // root CLAUDE.md "Authorisation": scoped to the encounter's own
    // department, not just "holds CLINICAL_NOTE_READ somewhere" — same
    // reasoning as ConsultationRecordController's encounter-scoped reads.
    @GetMapping("/api/encounters/{encounterId}/vital-signs")
    public List<VitalSignsResponse> byEncounter(@PathVariable UUID encounterId, @AuthenticationPrincipal Actor actor) {
        EncounterContext context = encounterFacade.findContext(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!authorizationService.hasPermission(actor, "CLINICAL_NOTE_READ", ScopeRef.of(ScopeType.DEPARTMENT, context.departmentId()))) {
            throw new AccessDeniedException("Missing permission: CLINICAL_NOTE_READ");
        }
        return vitalSignsRepository.findByEncounterId(encounterId).stream().map(this::toResponse).toList();
    }

    // 07-doctor.md §5.1 (this slice): a doctor may capture vitals during
    // consultation — optional in the workflow (triage-recorded vitals are
    // the default), but when submitted every one of the six primary vitals
    // is required exactly as WardVitalsController demands. Context is
    // pinned to CONSULTATION server-side; the doctor cannot set it.
    @PostMapping("/api/encounters/{encounterId}/vital-signs")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID recordForEncounter(
            @PathVariable UUID encounterId,
            @Valid @RequestBody RecordConsultationVitalsRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        EncounterContext context = encounterFacade.findContext(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!authorizationService.hasPermission(actor, "VITALS_CREATE", ScopeRef.of(ScopeType.DEPARTMENT, context.departmentId()))) {
            throw new AccessDeniedException("Missing permission: VITALS_CREATE");
        }
        VitalSigns saved = recordVitalSignsService.record(new RecordVitalSignsCommand(
                encounterId, context.patientId(), VitalSignsContext.CONSULTATION, null,
                request.temperatureC(), request.systolicMmHg(), request.diastolicMmHg(),
                request.pulseBpm(), request.respiratoryRate(), request.spo2Pct(),
                request.weightKg(), request.heightCm(),
                request.avpu(), request.bloodGlucoseMmol(), request.painScore(),
                null, null,
                actor.userId(),
                request.occurredAt() != null ? request.occurredAt() : Instant.now()));
        return saved.id();
    }

    // 07-doctor.md §5.1: every field is optional — the doctor records whatever
    // they actually measured. The service persists exactly the readings given
    // and rejects a wholly empty submission (RecordVitalSignsService). This is
    // deliberately looser than triage's mandatory full set (SubmitTriageAssessment).
    public record RecordConsultationVitalsRequest(
            Double temperatureC,
            Double systolicMmHg,
            Double diastolicMmHg,
            Double pulseBpm,
            Double respiratoryRate,
            Double spo2Pct,
            Double weightKg,
            Double heightCm,
            Avpu avpu,
            Double bloodGlucoseMmol,
            Integer painScore,
            Instant occurredAt
    ) {
    }

    private VitalSignsResponse toResponse(VitalSigns vitals) {
        Map<String, Double> readings = vitals.readings().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.vitalType().name(), RecordedVital::value));
        List<String> abnormalVitalTypes = vitals.readings().stream()
                .filter(RecordedVital::abnormal)
                .map(r -> r.vitalType().name())
                .toList();
        return new VitalSignsResponse(vitals.id(), vitals.encounterId(), vitals.context(), readings,
                abnormalVitalTypes,
                vitals.weightKg(), vitals.heightCm(), vitals.bmi(), vitals.avpu(), vitals.bloodGlucoseMmol(),
                vitals.recordedAt(), vitals.occurredAt());
    }

    public record VitalSignsResponse(
            UUID id, UUID encounterId, com.adags.modules.clinical.domain.VitalSignsContext context,
            Map<String, Double> readings,
            // Vital type names (matching the readings map keys) whose value fell outside the
            // patient's age-banded reference range. Populated per-reading server-side by
            // VitalReferenceRangeEvaluator at record time; frontends render the warning.
            List<String> abnormalVitalTypes,
            Double weightKg, Double heightCm, Double bmi,
            com.adags.modules.clinical.domain.Avpu avpu, Double bloodGlucoseMmol,
            Instant recordedAt, Instant occurredAt
    ) {
    }
}
