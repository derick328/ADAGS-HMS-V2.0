package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.MisclassificationDetectionReportService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 12-pharmacist.md §10.6: "Routed to CLINICAL_LEAD and the pharmacist-in-
// charge" — gated by ITEM_CLASSIFY, the closest existing permission to
// that authority (no dedicated CLINICAL_LEAD-scoped code exists for this
// report in the catalogue).
@RestController
@RequestMapping("/api/items/misclassification-report")
public class MisclassificationReportController {

    private final MisclassificationDetectionReportService reportService;
    private final AuthorizationService authorizationService;

    public MisclassificationReportController(
            MisclassificationDetectionReportService reportService, AuthorizationService authorizationService
    ) {
        this.reportService = reportService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public MisclassificationReportResponse get(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "ITEM_CLASSIFY")) {
            throw new AccessDeniedException("Missing permission: ITEM_CLASSIFY");
        }
        return MisclassificationReportResponse.from(reportService.generate());
    }
}
