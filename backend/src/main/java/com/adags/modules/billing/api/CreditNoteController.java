package com.adags.modules.billing.api;

import com.adags.modules.billing.application.IssueCreditNoteCommand;
import com.adags.modules.billing.application.IssueCreditNoteService;
import com.adags.modules.billing.application.ReviewCreditNoteCommand;
import com.adags.modules.billing.application.ReviewCreditNoteService;
import com.adags.modules.billing.domain.CreditNote;
import com.adags.modules.billing.domain.CreditNoteRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// Phase 7 item 1: "ACCOUNTANT authority, reason required. CHECK
// (approved_by != issued_by) above the same configurable threshold as
// write-offs." INVOICE_CREDIT_NOTE_ISSUE (V44) is ACCOUNTANT's; the
// second signature is a distinct holder of the same permission, enforced
// by ReviewCreditNoteService and the credit_note CHECK constraint.
@RestController
@RequestMapping("/api/billing")
public class CreditNoteController {

    private final IssueCreditNoteService issueCreditNoteService;
    private final ReviewCreditNoteService reviewCreditNoteService;
    private final CreditNoteRepository creditNoteRepository;
    private final AuthorizationService authorizationService;

    public CreditNoteController(
            IssueCreditNoteService issueCreditNoteService,
            ReviewCreditNoteService reviewCreditNoteService,
            CreditNoteRepository creditNoteRepository,
            AuthorizationService authorizationService
    ) {
        this.issueCreditNoteService = issueCreditNoteService;
        this.reviewCreditNoteService = reviewCreditNoteService;
        this.creditNoteRepository = creditNoteRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/invoices/{invoiceId}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditNoteResponse issue(
            @PathVariable UUID invoiceId, @Valid @RequestBody IssueCreditNoteRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "INVOICE_CREDIT_NOTE_ISSUE");
        CreditNote creditNote = issueCreditNoteService.issue(new IssueCreditNoteCommand(
                invoiceId, request.amountMinor(), request.reason(), request.reference(), actor.userId()));
        return CreditNoteResponse.from(creditNote);
    }

    @GetMapping("/credit-notes/pending")
    public List<CreditNoteResponse> pending(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_CREDIT_NOTE_ISSUE");
        return creditNoteRepository.findPending().stream().map(CreditNoteResponse::from).toList();
    }

    @GetMapping("/credit-notes/{id}")
    public CreditNoteResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_CREDIT_NOTE_ISSUE");
        return creditNoteRepository.findById(id)
                .map(CreditNoteResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Credit note not found: " + id));
    }

    @PostMapping("/credit-notes/{id}/review")
    public CreditNoteResponse review(
            @PathVariable UUID id, @Valid @RequestBody ReviewCreditNoteRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "INVOICE_CREDIT_NOTE_ISSUE");
        CreditNote creditNote = reviewCreditNoteService.review(
                new ReviewCreditNoteCommand(id, request.decision(), actor.userId(), request.decisionNote()));
        return CreditNoteResponse.from(creditNote);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
