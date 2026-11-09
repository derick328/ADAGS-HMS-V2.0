package com.adags.modules.encounters.api;

import com.adags.modules.encounters.application.DepartmentManagementService;
import com.adags.modules.encounters.DepartmentFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// 05-receptionist.md §5.2: "Send to triage — one click, no form." POST
// /api/encounters requires a departmentId, so the frontend needs to resolve
// one without asking — this is that list, auto-selected when there's
// exactly one active department (today: always, single-facility) and a
// picker only if there's ever more than one. No dedicated department
// module or permission exists yet; gated on ENCOUNTER_OPEN, the one action
// this list actually serves.
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;
    private final DepartmentManagementService management;

    public DepartmentController(NamedParameterJdbcTemplate jdbcTemplate, AuthorizationService authorizationService, DepartmentManagementService management) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
        this.management = management;
    }


    @GetMapping("/categories")
    public List<DepartmentManagementService.Category> categories(@AuthenticationPrincipal Actor actor) {
        requireCatalogue(actor);
        return management.categories();
    }

    @GetMapping("/catalogue")
    public List<DepartmentManagementService.Unit> catalogue(@AuthenticationPrincipal Actor actor) {
        requireCatalogue(actor);
        var assignedIds = management.assignments(actor.userId()).stream()
            .map(DepartmentFacade.Assignment::departmentId).collect(java.util.stream.Collectors.toSet());
        return management.units(false).stream().filter(u -> authorizationService.hasAnyPermission(actor,
            ScopeRef.of(ScopeType.DEPARTMENT,u.id()), "USER_MANAGE", "ENCOUNTER_OPEN", "APPOINTMENT_CREATE_FOLLOWUP")
            || assignedIds.contains(u.id())).toList();
    }

    @GetMapping("/mine")
    public List<DepartmentFacade.Assignment> mine(@AuthenticationPrincipal Actor actor) {
        requireCatalogue(actor);
        return management.assignments(actor.userId());
    }

    private void requireCatalogue(Actor actor) {
        if (!authorizationService.hasAnyPermission(actor, "USER_MANAGE", "ENCOUNTER_OPEN", "APPOINTMENT_CREATE_FOLLOWUP"))
            throw new AccessDeniedException("Missing department catalogue permission.");
    }

    @GetMapping
    public List<DepartmentResponse> list(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "ENCOUNTER_OPEN")) {
            throw new AccessDeniedException("Missing permission: ENCOUNTER_OPEN");
        }
        return jdbcTemplate.query(
                "SELECT id, code, name FROM department WHERE active = true AND consultation_enabled = true ORDER BY name",
                (rs, rowNum) -> new DepartmentResponse(
                        (UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name"))).stream()
                .filter(d -> authorizationService.hasPermission(actor,"ENCOUNTER_OPEN",
                    ScopeRef.of(ScopeType.DEPARTMENT,d.id()))).toList();
    }
}
