package com.adags.modules.billing.api;

import com.adags.modules.billing.application.RequestRefundCommand;
import com.adags.modules.billing.application.RequestRefundService;
import com.adags.modules.billing.application.ReviewRefundCommand;
import com.adags.modules.billing.application.ReviewRefundService;
import com.adags.modules.billing.domain.Refund;
import com.adags.modules.billing.domain.RefundRepository;
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

// Phase 7 item 1: "Money going back out. Request -> ACCOUNTANT approves.
// Never a receptionist action. Second signature above the threshold."
// PAYMENT_REFUND (V5) is already ACCOUNTANT's and was never granted to
// RECEPTIONIST — "never a receptionist action" holds without a new
// permission.
@RestController
@RequestMapping("/api/billing")
public class RefundController {

    private final RequestRefundService requestRefundService;
    private final ReviewRefundService reviewRefundService;
    private final RefundRepository refundRepository;
    private final AuthorizationService authorizationService;

    public RefundController(
            RequestRefundService requestRefundService,
            ReviewRefundService reviewRefundService,
            RefundRepository refundRepository,
            AuthorizationService authorizationService
    ) {
        this.requestRefundService = requestRefundService;
        this.reviewRefundService = reviewRefundService;
        this.refundRepository = refundRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/payment-allocations/{paymentAllocationId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse request(
            @PathVariable UUID paymentAllocationId, @Valid @RequestBody RequestRefundRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "PAYMENT_REFUND");
        Refund refund = requestRefundService.request(
                new RequestRefundCommand(paymentAllocationId, request.amountMinor(), request.reason(), actor.userId()));
        return RefundResponse.from(refund);
    }

    @GetMapping("/refunds/pending")
    public List<RefundResponse> pending(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "PAYMENT_REFUND");
        return refundRepository.findPending().stream().map(RefundResponse::from).toList();
    }

    @GetMapping("/refunds/{id}")
    public RefundResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "PAYMENT_REFUND");
        return refundRepository.findById(id)
                .map(RefundResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Refund not found: " + id));
    }

    @PostMapping("/refunds/{id}/review")
    public RefundResponse review(
            @PathVariable UUID id, @Valid @RequestBody ReviewRefundRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "PAYMENT_REFUND");
        Refund refund = reviewRefundService.review(
                new ReviewRefundCommand(id, request.decision(), actor.userId(), request.decisionNote()));
        return RefundResponse.from(refund);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
