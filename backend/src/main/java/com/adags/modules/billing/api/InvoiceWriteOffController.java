package com.adags.modules.billing.api;

import com.adags.modules.billing.application.RequestInvoiceWriteOffCommand;
import com.adags.modules.billing.application.RequestInvoiceWriteOffService;
import com.adags.modules.billing.application.ReviewInvoiceWriteOffCommand;
import com.adags.modules.billing.application.ReviewInvoiceWriteOffService;
import com.adags.modules.billing.domain.InvoiceWriteOff;
import com.adags.modules.billing.domain.InvoiceWriteOffRepository;
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

// Phase 7 item 1: "Write-off with reason, ACCOUNTANT authority, second
// signature above a configurable threshold." INVOICE_WRITE_OFF (V5) is
// already ACCOUNTANT's; the second signature is a distinct holder of the
// same permission, enforced by ReviewInvoiceWriteOffService and the
// invoice_write_off CHECK constraint (V43), not by a separate permission.
@RestController
@RequestMapping("/api/billing")
public class InvoiceWriteOffController {

    private final RequestInvoiceWriteOffService requestInvoiceWriteOffService;
    private final ReviewInvoiceWriteOffService reviewInvoiceWriteOffService;
    private final InvoiceWriteOffRepository invoiceWriteOffRepository;
    private final AuthorizationService authorizationService;

    public InvoiceWriteOffController(
            RequestInvoiceWriteOffService requestInvoiceWriteOffService,
            ReviewInvoiceWriteOffService reviewInvoiceWriteOffService,
            InvoiceWriteOffRepository invoiceWriteOffRepository,
            AuthorizationService authorizationService
    ) {
        this.requestInvoiceWriteOffService = requestInvoiceWriteOffService;
        this.reviewInvoiceWriteOffService = reviewInvoiceWriteOffService;
        this.invoiceWriteOffRepository = invoiceWriteOffRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/invoices/{invoiceId}/write-offs")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceWriteOffResponse request(
            @PathVariable UUID invoiceId, @Valid @RequestBody RequestInvoiceWriteOffRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "INVOICE_WRITE_OFF");
        InvoiceWriteOff writeOff = requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(invoiceId, request.amountMinor(), request.reason(), actor.userId()));
        return InvoiceWriteOffResponse.from(writeOff);
    }

    @GetMapping("/write-offs/pending")
    public List<InvoiceWriteOffResponse> pending(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_WRITE_OFF");
        return invoiceWriteOffRepository.findPending().stream().map(InvoiceWriteOffResponse::from).toList();
    }

    @GetMapping("/write-offs/{id}")
    public InvoiceWriteOffResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_WRITE_OFF");
        return invoiceWriteOffRepository.findById(id)
                .map(InvoiceWriteOffResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Write-off not found: " + id));
    }

    @PostMapping("/write-offs/{id}/review")
    public InvoiceWriteOffResponse review(
            @PathVariable UUID id, @Valid @RequestBody ReviewInvoiceWriteOffRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "INVOICE_WRITE_OFF");
        InvoiceWriteOff writeOff = reviewInvoiceWriteOffService.review(
                new ReviewInvoiceWriteOffCommand(id, request.decision(), actor.userId(), request.decisionNote()));
        return InvoiceWriteOffResponse.from(writeOff);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
