package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.TriageAssessmentDraft;
import com.adags.modules.encounters.domain.TriageAssessmentDraftRepository;
import com.adags.modules.identity.IdentityFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 06-triage.md §1/§3: autosave for the (not-yet-built) Assessment Form,
// plus the read side of the same table the queue uses for its "being
// assessed by" advisory line (§3) — never a lock, root CLAUDE.md
// "Concurrency" note in EncounterRepository.assignProviderAndType is the
// real guard against two submits racing, not anything here.
@RestController
public class TriageAssessmentDraftController {

    private final TriageAssessmentDraftRepository draftRepository;
    private final EncounterRepository encounterRepository;
    private final IdentityFacade identityFacade;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public TriageAssessmentDraftController(
            TriageAssessmentDraftRepository draftRepository,
            EncounterRepository encounterRepository,
            IdentityFacade identityFacade,
            AuthorizationService authorizationService,
            ObjectMapper objectMapper
    ) {
        this.draftRepository = draftRepository;
        this.encounterRepository = encounterRepository;
        this.identityFacade = identityFacade;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    @PutMapping("/api/encounters/{encounterId}/triage-draft")
    public TriageAssessmentDraftResponse upsert(
            @PathVariable UUID encounterId,
            @Valid @RequestBody TriageAssessmentDraftRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        Encounter encounter = requireEncounter(encounterId);
        requireAssess(actor, encounter);
        TriageAssessmentDraft draft = draftRepository.upsert(
                encounterId, actor.userId(), request.priority(), request.modeOfAmbulation(), writeJson(request.formState()));
        return toResponse(draft);
    }

    // 200 with an empty body when no draft exists yet — same "not found is
    // a normal outcome" convention as EncounterController#openForPatient,
    // not a 404: a nurse opening a never-touched patient is the ordinary case.
    @GetMapping("/api/encounters/{encounterId}/triage-draft")
    public TriageAssessmentDraftResponse findOwn(@PathVariable UUID encounterId, @AuthenticationPrincipal Actor actor) {
        Encounter encounter = requireEncounter(encounterId);
        requireAssess(actor, encounter);
        return draftRepository.findByEncounterAndAuthor(encounterId, actor.userId())
                .map(this::toResponse)
                .orElse(null);
    }

    @GetMapping("/api/triage-assessment-drafts/active-summaries")
    public List<TriageAssessmentDraftSummaryResponse> activeSummaries(
            @RequestParam List<UUID> encounterIds,
            @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "TRIAGE_QUEUE_READ")) {
            throw new AccessDeniedException("Missing permission: TRIAGE_QUEUE_READ");
        }
        return draftRepository.findLatestByEncounterIds(encounterIds).stream()
                .map(draft -> new TriageAssessmentDraftSummaryResponse(
                        draft.encounterId(), draft.authorId(),
                        identityFacade.findUsername(draft.authorId()).orElse("another nurse"),
                        draft.priority(), draft.modeOfAmbulation(), draft.updatedAt()))
                .toList();
    }

    private Encounter requireEncounter(UUID encounterId) {
        return encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
    }

    private void requireAssess(Actor actor, Encounter encounter) {
        boolean allowed = authorizationService.hasPermission(
                actor, "TRIAGE_ASSESS", ScopeRef.of(ScopeType.DEPARTMENT, encounter.departmentId()));
        if (!allowed) {
            throw new AccessDeniedException("Missing permission: TRIAGE_ASSESS");
        }
    }

    private TriageAssessmentDraftResponse toResponse(TriageAssessmentDraft draft) {
        return new TriageAssessmentDraftResponse(
                draft.encounterId(), draft.authorId(), draft.priority(), draft.modeOfAmbulation(),
                readJson(draft.formStateJson()), draft.updatedAt());
    }

    private String writeJson(Map<String, Object> formState) {
        try {
            return objectMapper.writeValueAsString(formState == null ? Map.of() : formState);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize triage draft form state", e);
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize triage draft form state", e);
        }
    }
}
