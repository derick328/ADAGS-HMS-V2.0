package com.adags.modules.billing.api;

import com.adags.modules.billing.application.RequestInvoiceCancellationCommand;
import com.adags.modules.billing.application.RequestInvoiceCancellationService;
import com.adags.modules.billing.application.ReviewInvoiceCancellationCommand;
import com.adags.modules.billing.application.ReviewInvoiceCancellationService;
import com.adags.modules.billing.domain.InvoiceCancellationRequest;
import com.adags.modules.billing.domain.InvoiceCancellationRequestRepository;
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

// 18-admin.md §6: receptionist requests (INVOICE_CANCEL_REQUEST, already
// RECEPTIONIST's and — V45 — ADMIN's, for §6.1 reconsideration); admin
// decides (INVOICE_CANCEL_APPROVE).
@RestController
@RequestMapping("/api/billing")
public class InvoiceCancellationController {

    private final RequestInvoiceCancellationService requestInvoiceCancellationService;
    private final ReviewInvoiceCancellationService reviewInvoiceCancellationService;
    private final InvoiceCancellationRequestRepository cancellationRequestRepository;
    private final AuthorizationService authorizationService;

    public InvoiceCancellationController(
            RequestInvoiceCancellationService requestInvoiceCancellationService,
            ReviewInvoiceCancellationService reviewInvoiceCancellationService,
            InvoiceCancellationRequestRepository cancellationRequestRepository,
            AuthorizationService authorizationService
    ) {
        this.requestInvoiceCancellationService = requestInvoiceCancellationService;
        this.reviewInvoiceCancellationService = reviewInvoiceCancellationService;
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/invoices/{invoiceId}/cancellation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceCancellationRequestResponse request(
            @PathVariable UUID invoiceId, @Valid @RequestBody RequestInvoiceCancellationRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "INVOICE_CANCEL_REQUEST");
        InvoiceCancellationRequest saved = requestInvoiceCancellationService.request(new RequestInvoiceCancellationCommand(
                invoiceId, request.reason(), actor.userId(), request.supersedesRequestId()));
        return InvoiceCancellationRequestResponse.from(saved);
    }

    // The site-wide pending badge and the admin's review queue.
    @GetMapping("/cancellation-requests/pending")
    public List<InvoiceCancellationRequestResponse> pending(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_CANCEL_APPROVE");
        return cancellationRequestRepository.findPending().stream().map(InvoiceCancellationRequestResponse::from).toList();
    }

    @GetMapping("/cancellation-requests/{id}")
    public InvoiceCancellationRequestResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "INVOICE_CANCEL_APPROVE");
        return cancellationRequestRepository.findById(id)
                .map(InvoiceCancellationRequestResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Cancellation request not found: " + id));
    }

    @PostMapping("/cancellation-requests/{id}/review")
    public InvoiceCancellationRequestResponse review(
            @PathVariable UUID id, @Valid @RequestBody ReviewInvoiceCancellationRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "INVOICE_CANCEL_APPROVE");
        InvoiceCancellationRequest decided = reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(id, request.decision(), actor.userId(), request.reviewNote()));
        return InvoiceCancellationRequestResponse.from(decided);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
