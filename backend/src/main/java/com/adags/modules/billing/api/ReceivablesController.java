package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.AgingBucket;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.ReceivablesRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 18-admin.md (Phase 7 item 1): "Aged receivables view: current, 30, 60,
// 90+, by payer category." RECEIVABLE_MANAGE (V5) is already ACCOUNTANT's —
// "Work post-discharge receivables."
@RestController
@RequestMapping("/api/billing/receivables")
public class ReceivablesController {

    private final ReceivablesRepository receivablesRepository;
    private final AuthorizationService authorizationService;

    public ReceivablesController(ReceivablesRepository receivablesRepository, AuthorizationService authorizationService) {
        this.receivablesRepository = receivablesRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/aging-summary")
    public List<AgingSummaryResponse> agingSummary(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "RECEIVABLE_MANAGE");
        return receivablesRepository.summarizeAging().stream().map(AgingSummaryResponse::from).toList();
    }

    @GetMapping
    public List<ReceivableInvoiceResponse> list(
            @RequestParam(required = false) PayerCategory payerCategory,
            @RequestParam(required = false) AgingBucket bucket,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "RECEIVABLE_MANAGE");
        return receivablesRepository.findReceivables(payerCategory, bucket).stream()
                .map(ReceivableInvoiceResponse::from).toList();
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
