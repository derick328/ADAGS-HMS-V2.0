package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.TriageAssessment;
import com.adags.modules.encounters.domain.TriageAssessmentRepository;
import com.adags.modules.identity.IdentityFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.authz.PolicyResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 06-triage.md §1: "Completed Assessments" — read-only. Named
// TRIAGE_HISTORY_READ in the permission catalogue, distinct from
// TRIAGE_ASSESS (recording one) — separate controller from
// SubmitTriageAssessmentController (write) for the same reason
// TriageAssessmentDraftController and ReferralCandidateController are
// each their own class: one capability per controller, this session's
// established convention.
@RestController
public class TriageAssessmentController {

    private final TriageAssessmentRepository triageAssessmentRepository;
    private final EncounterRepository encounterRepository;
    private final IdentityFacade identityFacade;
    private final AuthorizationService authorizationService;
    private final AuditRecorder historyAudit;

    public TriageAssessmentController(
            TriageAssessmentRepository triageAssessmentRepository,
            EncounterRepository encounterRepository,
            IdentityFacade identityFacade,
            AuthorizationService authorizationService,
            AuditRecorder historyAudit
    ) {
        this.triageAssessmentRepository = triageAssessmentRepository;
        this.encounterRepository = encounterRepository;
        this.identityFacade = identityFacade;
        this.authorizationService = authorizationService;
        this.historyAudit = historyAudit;
    }

    // §7.12 ("this nurse's work only, or the whole shift's?") resolved as
    // one endpoint, two callers: pass assessedBy=self for "mine", omit it
    // for "the whole shift." ownOnly=true additionally forces assessedBy
    // to the caller's own id server-side — a nurse cannot pass someone
    // else's userId to read their history (TRIAGE_HISTORY_READ is a "read
    // the roster's shared list" grant, not "read anyone's individually").
    @GetMapping("/api/triage-assessments")
    public List<CompletedTriageAssessmentResponse> search(
            @RequestParam(required = false) Boolean ownOnly,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant assessedFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant assessedTo,
            @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "TRIAGE_HISTORY_READ")) {
            throw new AccessDeniedException("Missing permission: TRIAGE_HISTORY_READ");
        }
        UUID assessedBy = Boolean.TRUE.equals(ownOnly) ? actor.userId() : null;
        return triageAssessmentRepository.search(assessedBy, assessedFrom, assessedTo).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/api/encounters/{encounterId}/triage-assessments/history")
    public List<CompletedTriageAssessmentResponse> byEncounter(@PathVariable UUID encounterId,
                                                                 @AuthenticationPrincipal Actor actor) {
        var encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Encounter not found: " + encounterId));
        if (authorizationService.checkPatientAccess(actor, "CLINICAL_NOTE_READ", encounter.patientId(),
                encounter.scope()) != PolicyResult.ALLOWED) {
            throw new AccessDeniedException("Missing clinical history access");
        }
        historyAudit.record(actor.userId(), "CLINICAL_HISTORY_READ", "encounter", encounterId, encounter.patientId());
        return triageAssessmentRepository.findByEncounterId(encounterId).stream()
                .map(this::toResponse).toList();
    }

    private CompletedTriageAssessmentResponse toResponse(TriageAssessment assessment) {
        UUID patientId = encounterRepository.findById(assessment.encounterId())
                .map(e -> e.patientId())
                .orElse(null);
        String assessedByUsername = identityFacade.findUsername(assessment.assessedBy()).orElse(null);
        return CompletedTriageAssessmentResponse.from(assessment, patientId, assessedByUsername);
    }
}
