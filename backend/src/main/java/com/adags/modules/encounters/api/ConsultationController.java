package com.adags.modules.encounters.api;

import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.encounters.application.FinalizeConsultationCommand;
import com.adags.modules.encounters.application.FinalizeConsultationService;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.encounters.application.MarkAwaitingResultsService;
import com.adags.modules.encounters.application.OpenConsultationService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

// 07-doctor.md §2/§7/§10: the encounter-state half of the consultation
// lifecycle — the two things only the encounters module may do (mutate
// Encounter.state, root CLAUDE.md "Modules"). Everything that doesn't
// touch encounter state (autosave, diagnosis, incomplete list) lives in
// clinical/api/ConsultationRecordController instead, matching the same
// split OpenConsultationService/FinalizeConsultationService already draw
// against ClinicalFacade.
@RestController
public class ConsultationController {

    private final EncounterRepository encounterRepository;
    private final OpenConsultationService openConsultationService;
    private final FinalizeConsultationService finalizeConsultationService;
    private final MarkAwaitingResultsService markAwaitingResultsService;
    private final ClinicalFacade clinicalFacade;
    private final AuthorizationService authorizationService;

    public ConsultationController(
            EncounterRepository encounterRepository,
            OpenConsultationService openConsultationService,
            FinalizeConsultationService finalizeConsultationService,
            MarkAwaitingResultsService markAwaitingResultsService,
            ClinicalFacade clinicalFacade,
            AuthorizationService authorizationService
    ) {
        this.encounterRepository = encounterRepository;
        this.openConsultationService = openConsultationService;
        this.finalizeConsultationService = finalizeConsultationService;
        this.markAwaitingResultsService = markAwaitingResultsService;
        this.clinicalFacade = clinicalFacade;
        this.authorizationService = authorizationService;
    }

    // 07-doctor.md §2's entry guard: canOpenConsultation = state ==
    // AWAITING_CONSULT OR an active bypass (already satisfied by the state
    // machine reaching AWAITING_CONSULT at all — see OpenConsultationService's
    // own comment). First entry from the queue transitions AWAITING_CONSULT
    // -> IN_CONSULT via OpenConsultationService; a later re-entry (page
    // reload, tab switch, resuming after ordering labs) while already
    // IN_CONSULT resumes the same OPEN ConsultationRecord without touching
    // encounter state again. Any other state means the guard failed — the
    // frontend bounces back to the queue with the error message.
    @PostMapping("/api/encounters/{id}/consultation/open")
    public OpenConsultationResponse open(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Encounter encounter = requireEncounter(id);
        requirePermission(actor, "CONSULTATION_CREATE", encounter.scope());

        UUID consultationRecordId;
        if (encounter.state() == EncounterState.AWAITING_CONSULT) {
            consultationRecordId = openConsultationService.open(id, actor.userId());
            encounter = requireEncounter(id);
        } else if (encounter.state() == EncounterState.IN_CONSULT) {
            consultationRecordId = clinicalFacade.openConsultationRecord(id, actor.userId());
        } else {
            throw new InvalidEncounterTransitionException(encounter.state(), EncounterState.IN_CONSULT);
        }
        return new OpenConsultationResponse(consultationRecordId, EncounterResponse.from(encounter));
    }

    // §7's disposition table + §10: "finalizing the record is not closing
    // the encounter." FinalizeConsultationService validates the note
    // (required fields, retrospective note) and transitions the encounter
    // to the disposition's target state in one transaction — if the note
    // isn't ready, the encounter does not move.
    @PostMapping("/api/encounters/{id}/consultation/finalize")
    public EncounterResponse finalize(
            @PathVariable UUID id, @Valid @RequestBody FinalizeConsultationRequest request, @AuthenticationPrincipal Actor actor
    ) {
        Encounter encounter = requireEncounter(id);
        requirePermission(actor, "CONSULTATION_FINALIZE", encounter.scope());

        Encounter updated = finalizeConsultationService.finalize(new FinalizeConsultationCommand(
                id, actor.userId(), request.disposition(), request.retrospectiveCompletion(), request.retrospectiveNote()));
        return EncounterResponse.from(updated);
    }

    // §8.2: "order labs -> send -> finalize the note -> encounter moves to
    // AWAITING_RESULTS." A separate action from finalize rather than a
    // disposition value — MarkAwaitingResultsService's own comment notes
    // it isn't tied to order placement yet (no Orders module trigger), so
    // this is the doctor's explicit "I'm waiting on results for this
    // patient" action, invokable independently of finalizing the note.
    @PostMapping("/api/encounters/{id}/consultation/mark-awaiting-results")
    public EncounterResponse markAwaitingResults(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Encounter encounter = requireEncounter(id);
        requirePermission(actor, "CONSULTATION_FINALIZE", encounter.scope());

        markAwaitingResultsService.markAwaitingResults(id, actor.userId());
        return EncounterResponse.from(requireEncounter(id));
    }

    private Encounter requireEncounter(UUID id) {
        return encounterRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + id));
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeRef scope) {
        if (!authorizationService.hasPermission(actor, permissionCode, scope)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }

    public record OpenConsultationResponse(UUID consultationRecordId, EncounterResponse encounter) {
    }

    public record FinalizeConsultationRequest(
            @NotNull com.adags.modules.encounters.domain.DispositionOutcome disposition,
            boolean retrospectiveCompletion,
            String retrospectiveNote
    ) {
    }
}
