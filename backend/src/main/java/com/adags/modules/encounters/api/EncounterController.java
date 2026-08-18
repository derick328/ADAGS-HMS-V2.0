package com.adags.modules.encounters.api;

import com.adags.modules.encounters.application.CloseEncounterCommand;
import com.adags.modules.encounters.application.CloseEncounterService;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

// 05-receptionist.md §8. GET /encounters?state=&date= (the "here now" panel
// projection), and every transition beyond open/dismiss (triage, payment,
// consult, ...), are out of this slice — no module exists yet to drive
// them. See backend/CLAUDE.md for the full transition graph.
//
// Every check here is scoped to the encounter's department — root CLAUDE.md
// "Authorisation": scope resolution walks WARD -> DEPARTMENT -> FACILITY, so
// a department-scoped role assignment covers it, a facility-scoped one
// covers it too, but a different department's assignment does not.
@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private final OpenEncounterService openEncounterService;
    private final CloseEncounterService closeEncounterService;
    private final EncounterRepository encounterRepository;
    private final AuthorizationService authorizationService;

    public EncounterController(
            OpenEncounterService openEncounterService,
            CloseEncounterService closeEncounterService,
            EncounterRepository encounterRepository,
            AuthorizationService authorizationService
    ) {
        this.openEncounterService = openEncounterService;
        this.closeEncounterService = closeEncounterService;
        this.encounterRepository = encounterRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EncounterResponse open(@Valid @RequestBody OpenEncounterRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "ENCOUNTER_OPEN", ScopeType.DEPARTMENT, request.departmentId());
        OpenEncounterCommand command = new OpenEncounterCommand(
                request.patientId(), request.departmentId(), request.appointmentId(), actor.userId());
        Encounter encounter = openEncounterService.checkIn(command);
        return EncounterResponse.from(encounter);
    }

    @GetMapping("/{id}")
    public EncounterResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Encounter encounter = encounterRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + id));
        requirePermission(actor, "ENCOUNTER_OPEN", ScopeType.DEPARTMENT, encounter.departmentId());
        return EncounterResponse.from(encounter);
    }

    @PostMapping("/{id}/close")
    public EncounterResponse close(
            @PathVariable UUID id,
            @Valid @RequestBody CloseEncounterRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        Encounter encounter = encounterRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + id));
        requirePermission(actor, "ENCOUNTER_CLOSE_ADMIN", ScopeType.DEPARTMENT, encounter.departmentId());
        CloseEncounterCommand command = new CloseEncounterCommand(id, request.outcome(), request.reason(), actor.userId());
        Encounter closed = closeEncounterService.close(command);
        return EncounterResponse.from(closed);
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeType scopeType, UUID scopeId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(scopeType, scopeId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
