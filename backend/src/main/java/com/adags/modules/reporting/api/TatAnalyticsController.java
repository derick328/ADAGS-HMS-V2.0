package com.adags.modules.reporting.api;

import com.adags.modules.reporting.application.TatAnalyticsService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tat")
public class TatAnalyticsController {
    private static final Set<String> WORKFLOWS = Set.of("TRIAGE", "DOCTOR", "LABORATORY", "PHARMACY");
    private static final Set<String> STATUSES = Set.of("WAITING", "IN_PROGRESS", "AWAITING_RESULTS",
            "AWAITING_PAYMENT", "AWAITING_AUTHORIZATION", "COMPLETED", "CANCELLED", "INCOMPLETE");
    private final TatAnalyticsService service;
    private final AuthorizationService authorization;
    private final AuditRecorder audit;

    public TatAnalyticsController(TatAnalyticsService service, AuthorizationService authorization, AuditRecorder audit) {
        this.service = service;
        this.authorization = authorization;
        this.audit = audit;
    }

    @GetMapping("/timezone")
    public TatTimezone timezone(@RequestParam UUID facilityId, @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        return new TatTimezone(service.facilityTimezone(facilityId));
    }

    public record TatTimezone(String timezone) {}

    @GetMapping("/overview")
    public TatAnalyticsService.TatOverview overview(@RequestParam UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        return service.overview(facilityId, from, to);
    }

    @GetMapping("/departments")
    public List<TatAnalyticsService.TatDepartment> departments(@RequestParam UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String workflowType, @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        return service.departments(facilityId, from, to, workflow(workflowType));
    }

    @GetMapping("/departments/{workflowType}/users")
    public List<TatAnalyticsService.TatUserSummary> departmentUsers(@PathVariable String workflowType,
            @RequestParam UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        return service.departmentUsers(facilityId, from, to, workflow(workflowType));
    }

    @GetMapping("/departments/{workflowType}/users/{userId}/patients")
    public List<TatAnalyticsService.TatUserPatient> userPatients(@PathVariable String workflowType,
            @PathVariable UUID userId, @RequestParam UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        var result = service.userPatients(facilityId, from, to, workflow(workflowType), userId, page);
        result.stream().map(TatAnalyticsService.TatUserPatient::patientId).distinct()
                .forEach(patientId -> audit.record(actor.userId(), "TAT_USER_PATIENTS_READ", "patient", patientId, patientId));
        return result;
    }

    @GetMapping("/trend")
    public List<TatAnalyticsService.TatTrend> trend(@RequestParam UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String workflowType, @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        return service.trend(facilityId, from, to, workflow(workflowType));
    }

    @GetMapping("/currently-waiting")
    public List<TatAnalyticsService.TatWaiting> currentlyWaiting(@RequestParam UUID facilityId,
            @RequestParam(required = false) String workflowType,
            @RequestParam(defaultValue = "50") int limit, @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        return service.currentlyWaiting(facilityId, workflow(workflowType), limit);
    }

    @GetMapping("/activity")
    public List<TatAnalyticsService.TatActivity> activity(@RequestParam UUID facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String workflowType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        if (status != null && !STATUSES.contains(status)) throw new IllegalArgumentException("Unknown workflow status.");
        return service.activity(facilityId, from, to, workflow(workflowType), status, page);
    }

    @GetMapping("/patients/{patientId}/encounters")
    public List<TatAnalyticsService.TatVisit> patientVisits(@PathVariable UUID patientId,
            @RequestParam UUID facilityId, @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        audit.record(actor.userId(), "TAT_PATIENT_HISTORY_READ", "patient", patientId, patientId);
        return service.patientVisits(facilityId, patientId);
    }

    @GetMapping("/encounters/{encounterId}")
    public TatAnalyticsService.TatJourney encounter(@PathVariable UUID encounterId,
            @RequestParam UUID facilityId, @AuthenticationPrincipal Actor actor) {
        requireRead(actor, facilityId);
        var result = service.encounterJourney(facilityId, encounterId);
        audit.record(actor.userId(), "TAT_ENCOUNTER_READ", "encounter", encounterId, null);
        return result;
    }

    private void requireRead(Actor actor, UUID facilityId) {
        if (!authorization.hasPermission(actor, "TAT_ANALYTICS_READ", ScopeRef.of(ScopeType.FACILITY, facilityId)))
            throw new AccessDeniedException("Missing permission: TAT_ANALYTICS_READ");
    }

    private static String workflow(String value) {
        if (value != null && !WORKFLOWS.contains(value)) throw new IllegalArgumentException("Unknown workflow type.");
        return value;
    }
}
