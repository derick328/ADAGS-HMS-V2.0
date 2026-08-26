package com.adags.modules.clinical.api;

import com.adags.modules.clinical.domain.RecordedVital;
import com.adags.modules.clinical.domain.VitalSigns;
import com.adags.modules.clinical.domain.VitalSignsRepository;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;

    public VitalSignsController(
            VitalSignsRepository vitalSignsRepository, EncounterFacade encounterFacade, AuthorizationService authorizationService
    ) {
        this.vitalSignsRepository = vitalSignsRepository;
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

    private VitalSignsResponse toResponse(VitalSigns vitals) {
        Map<String, Double> readings = vitals.readings().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.vitalType().name(), RecordedVital::value));
        return new VitalSignsResponse(vitals.id(), vitals.encounterId(), vitals.context(), readings,
                vitals.weightKg(), vitals.heightCm(), vitals.bmi(), vitals.avpu(), vitals.bloodGlucoseMmol(),
                vitals.recordedAt(), vitals.occurredAt());
    }

    public record VitalSignsResponse(
            UUID id, UUID encounterId, com.adags.modules.clinical.domain.VitalSignsContext context,
            Map<String, Double> readings, double weightKg, double heightCm, Double bmi,
            com.adags.modules.clinical.domain.Avpu avpu, Double bloodGlucoseMmol,
            Instant recordedAt, Instant occurredAt
    ) {
    }
}
