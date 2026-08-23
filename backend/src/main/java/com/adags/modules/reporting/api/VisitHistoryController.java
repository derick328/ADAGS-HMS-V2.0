package com.adags.modules.reporting.api;

import com.adags.modules.reporting.application.VisitHistoryService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// 05-receptionist.md §1.1 ⚠1 / §8: the restricted visit-history projection
// — no clinical narrative, diagnosis, vitals, or result ever leaves this
// endpoint (VisitHistoryEntry has no field for any of them). Mapped under
// /api/patients rather than /api/reporting to match the resource the spec
// names it against; the read model itself lives in reporting because it
// spans encounter/department/staff/invoice, none of which one module's
// facade alone could answer.
@RestController
@RequestMapping("/api/patients")
public class VisitHistoryController {

    private final VisitHistoryService visitHistoryService;
    private final AuthorizationService authorizationService;

    public VisitHistoryController(VisitHistoryService visitHistoryService, AuthorizationService authorizationService) {
        this.visitHistoryService = visitHistoryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{id}/visit-history")
    public List<VisitHistoryResponse> visitHistory(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "PATIENT_READ_VISIT_HISTORY")) {
            throw new AccessDeniedException("Missing permission: PATIENT_READ_VISIT_HISTORY");
        }
        return visitHistoryService.forPatient(id).stream().map(VisitHistoryResponse::from).toList();
    }
}
