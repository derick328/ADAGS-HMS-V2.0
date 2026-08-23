package com.adags.modules.billing.api;

import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.Payment;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 03-billing-addendum.md §1 / 05-receptionist.md §7.4: the line-picker
// payment screen's write side. PostPaymentService already exists, fully
// built (idempotency, exact-match-per-line, active-bypass partial
// exception, cashier-shift-open guard) — this is only the missing REST
// surface over it. Payment is patient-scoped, not invoice-scoped
// (Payment.java's own doc comment: one payment's allocations can cover
// lines across more than one invoice), so this is a top-level resource
// rather than nested under /api/invoices/{id} — 05-receptionist.md §8's
// older endpoint sketch predates that resolution (02-billing-architecture.md
// §12 ?Q1).
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PostPaymentService postPaymentService;
    private final AuthorizationService authorizationService;

    public PaymentController(PostPaymentService postPaymentService, AuthorizationService authorizationService) {
        this.postPaymentService = postPaymentService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse record(@Valid @RequestBody RecordPaymentRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "PAYMENT_CREATE")) {
            throw new AccessDeniedException("Missing permission: PAYMENT_CREATE");
        }
        var allocations = request.allocations().stream()
                .map(a -> new PostPaymentCommand.LineAllocationRequest(a.invoiceLineId(), a.amountMinor()))
                .toList();
        Payment payment = postPaymentService.recordPayment(new PostPaymentCommand(
                request.patientId(), request.method(), request.reference(), request.idempotencyKey(),
                allocations, actor.userId()));
        return PaymentResponse.from(payment);
    }
}
