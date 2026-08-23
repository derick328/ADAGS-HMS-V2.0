package com.adags.modules.billing.api;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.billing.application.ActivePaymentBypassOverviewService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 02-billing-architecture.md §5.4: the receptionist's emergency billing
// bar. No dedicated permission code exists for "read active bypasses" —
// reusing ENCOUNTER_OPEN, same reasoning as EncounterController's list
// endpoint (visibility-only; every permission check here is, per
// usePermission's own frontend contract).
@RestController
@RequestMapping("/api/payment-bypasses")
public class PaymentBypassController {

    private final ActivePaymentBypassOverviewService activePaymentBypassOverviewService;
    private final BillingFacade billingFacade;
    private final AuthorizationService authorizationService;

    public PaymentBypassController(
            ActivePaymentBypassOverviewService activePaymentBypassOverviewService,
            BillingFacade billingFacade,
            AuthorizationService authorizationService
    ) {
        this.activePaymentBypassOverviewService = activePaymentBypassOverviewService;
        this.billingFacade = billingFacade;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/active")
    public List<EmergencyBypassResponse> active(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "ENCOUNTER_OPEN")) {
            throw new AccessDeniedException("Missing permission: ENCOUNTER_OPEN");
        }
        return activePaymentBypassOverviewService.overview().stream().map(EmergencyBypassResponse::from).toList();
    }

    // 02-billing-architecture.md §8: "charity, staff benefit, ministry
    // directive" — an admin grants this directly, no second actor (unlike
    // CLINICIAN_AUTHORISED, which needs a Doctor workspace that doesn't
    // exist yet to receive a request). Scope is the whole encounter, same
    // as every other bypass source — the gate check
    // (active_payment_bypass_exists) doesn't distinguish by source.
    @PostMapping("/admin-waiver")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantAdminWaiverResponse grantAdminWaiver(@Valid @RequestBody GrantAdminWaiverRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "PAYMENT_BYPASS_WAIVE")) {
            throw new AccessDeniedException("Missing permission: PAYMENT_BYPASS_WAIVE");
        }
        return new GrantAdminWaiverResponse(
                billingFacade.createAdminWaiverPaymentBypass(request.encounterId(), request.reason(), actor.userId()));
    }
}
