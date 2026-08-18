package com.adags.modules.referencedata.api;

import com.adags.modules.referencedata.application.Icd10ImportResult;
import com.adags.modules.referencedata.application.Icd10ImportService;
import com.adags.modules.referencedata.application.Icd10SearchOutcome;
import com.adags.modules.referencedata.application.Icd10SearchService;
import com.adags.modules.referencedata.application.ProposeIcd10SynonymService;
import com.adags.modules.referencedata.application.ReviewIcd10SynonymService;
import com.adags.modules.referencedata.domain.Icd10Synonym;
import com.adags.modules.referencedata.domain.SynonymStatus;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 10-clinical-reference-data.md §4 items 3-5: search (§5), synonym
// propose-and-approve (§3), and import (§4.1). Search carries no
// permission check of its own — the picker is a shared lookup used from
// inside many already-gated screens (diagnosis entry, lab ordering, ...);
// §3's permission table only names PROPOSE/APPROVE/IMPORT/COMMON_MANAGE,
// not a separate "may search" permission, and the catalogue (V5
// migration) agrees.
@RestController
@RequestMapping("/api/reference/icd10")
public class Icd10ReferenceController {

    private final Icd10SearchService searchService;
    private final Icd10ImportService importService;
    private final ProposeIcd10SynonymService proposeSynonymService;
    private final ReviewIcd10SynonymService reviewSynonymService;
    private final AuthorizationService authorizationService;

    public Icd10ReferenceController(
            Icd10SearchService searchService,
            Icd10ImportService importService,
            ProposeIcd10SynonymService proposeSynonymService,
            ReviewIcd10SynonymService reviewSynonymService,
            AuthorizationService authorizationService
    ) {
        this.searchService = searchService;
        this.importService = importService;
        this.proposeSynonymService = proposeSynonymService;
        this.reviewSynonymService = reviewSynonymService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/search")
    public Icd10SearchResponse search(
            @RequestParam("q") String queryText,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @AuthenticationPrincipal Actor actor
    ) {
        Icd10SearchOutcome outcome = searchService.search(queryText, departmentId, actor.userId());
        return Icd10SearchResponse.from(outcome);
    }

    @PostMapping("/search/{logId}/selection")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void recordSelection(@PathVariable UUID logId, @Valid @RequestBody RecordSelectionRequest request) {
        searchService.recordSelection(logId, request.selectedCode());
    }

    @PostMapping("/import")
    public Icd10ImportResult importRelease(
            @Valid @RequestBody ImportIcd10ReleaseRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "REFERENCE_IMPORT");
        return importService.importRelease(request.release(), request.codes());
    }

    @PostMapping("/synonyms")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public SynonymResponse proposeSynonym(
            @Valid @RequestBody ProposeSynonymRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "REFERENCE_SYNONYM_PROPOSE");
        Icd10Synonym synonym = proposeSynonymService.propose(request.code(), request.term(), request.language(), actor.userId());
        return SynonymResponse.from(synonym);
    }

    @PostMapping("/synonyms/{id}/approve")
    public SynonymResponse approveSynonym(
            @PathVariable UUID id, @RequestBody(required = false) ReviewSynonymRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "REFERENCE_SYNONYM_APPROVE");
        String note = request == null ? null : request.reviewNote();
        Icd10Synonym synonym = reviewSynonymService.review(id, SynonymStatus.APPROVED, actor.userId(), note);
        return SynonymResponse.from(synonym);
    }

    @PostMapping("/synonyms/{id}/reject")
    public SynonymResponse rejectSynonym(
            @PathVariable UUID id, @RequestBody(required = false) ReviewSynonymRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "REFERENCE_SYNONYM_APPROVE");
        String note = request == null ? null : request.reviewNote();
        Icd10Synonym synonym = reviewSynonymService.review(id, SynonymStatus.REJECTED, actor.userId(), note);
        return SynonymResponse.from(synonym);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
