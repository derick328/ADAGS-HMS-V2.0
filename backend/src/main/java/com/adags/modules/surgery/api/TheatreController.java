package com.adags.modules.surgery.api;

import com.adags.modules.surgery.application.CreateTheatreCommand;
import com.adags.modules.surgery.application.CreateTheatreService;
import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// Theatre is administrative setup, defined by THEATRE_MANAGE (V50) — never
// by whoever schedules a procedure into it. Same split as WardController/
// WARD_BED_MANAGE.
@RestController
@RequestMapping("/api/theatres")
public class TheatreController {

    private final CreateTheatreService createTheatreService;
    private final TheatreRepository theatreRepository;
    private final AuthorizationService authorizationService;

    public TheatreController(
            CreateTheatreService createTheatreService,
            TheatreRepository theatreRepository,
            AuthorizationService authorizationService
    ) {
        this.createTheatreService = createTheatreService;
        this.theatreRepository = theatreRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TheatreResponse create(@Valid @RequestBody CreateTheatreRequest request, @AuthenticationPrincipal Actor actor) {
        requireManage(actor, request.facilityId());
        Theatre theatre = createTheatreService.create(new CreateTheatreCommand(request.facilityId(), request.name()));
        return TheatreResponse.from(theatre);
    }

    // Scheduling needs the list to offer a theatre picker; the admin
    // Theatres page needs the same list to manage them. Both permissions
    // are honoured here so an admin holding THEATRE_MANAGE (but never
    // scheduling) can still read what they administer, and a doctor
    // holding SURGERY_ORDER_CREATE can pick from it.
    //
    // SURGERY_ORDER_CREATE is checked unscoped — matching every other
    // doctor read endpoint (LabOrderController, NurseWorkloadController,
    // OrderDischargeService). A department-scoped specialist doctor
    // still needs to book a facility-level theatre for their patient,
    // and requiring the permission at FACILITY scope would exclude them
    // even though the write (POST /surgery-orders) is what actually
    // enforces per-encounter authorisation. THEATRE_MANAGE keeps its
    // FACILITY scope because the admin management surface is genuinely
    // facility-scoped.
    //
    // includeInactive is admin-only: an admin managing the roster needs
    // to see parked theatres to reactivate them, whereas a scheduler
    // must never see one that has been taken out of service.
    @GetMapping
    public List<TheatreResponse> listByFacility(
            @RequestParam UUID facilityId,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal Actor actor
    ) {
        ScopeRef facilityScope = ScopeRef.of(ScopeType.FACILITY, facilityId);
        boolean hasManage = authorizationService.hasPermission(actor, "THEATRE_MANAGE", facilityScope);
        if (includeInactive) {
            if (!hasManage) {
                throw new AccessDeniedException("Missing permission: THEATRE_MANAGE");
            }
            return theatreRepository.findAllByFacilityId(facilityId).stream().map(TheatreResponse::from).toList();
        }
        boolean allowed = hasManage
                || authorizationService.hasPermission(actor, "SURGERY_ORDER_CREATE");
        if (!allowed) {
            throw new AccessDeniedException("Missing permission: SURGERY_ORDER_CREATE or THEATRE_MANAGE");
        }
        return theatreRepository.findByFacilityId(facilityId).stream().map(TheatreResponse::from).toList();
    }

    // Root CLAUDE.md "Data": never a hard delete. Activate/deactivate is
    // the switch that decides whether the doctor's schedule-surgery
    // picker offers this theatre, without erasing any surgery order
    // that already booked it.
    @PostMapping("/{id}/activate")
    public TheatreResponse activate(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Theatre theatre = requireTheatreForManagement(id, actor);
        if (!theatre.active()) {
            theatreRepository.setActive(id, true);
        }
        return TheatreResponse.from(new Theatre(theatre.id(), theatre.facilityId(), theatre.name(), true,
                theatre.createdAt()));
    }

    @PostMapping("/{id}/deactivate")
    public TheatreResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Theatre theatre = requireTheatreForManagement(id, actor);
        if (theatre.active()) {
            theatreRepository.setActive(id, false);
        }
        return TheatreResponse.from(new Theatre(theatre.id(), theatre.facilityId(), theatre.name(), false,
                theatre.createdAt()));
    }

    private Theatre requireTheatreForManagement(UUID id, Actor actor) {
        Theatre theatre = theatreRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Theatre not found: " + id));
        requireManage(actor, theatre.facilityId());
        return theatre;
    }

    private void requireManage(Actor actor, UUID facilityId) {
        if (!authorizationService.hasPermission(actor, "THEATRE_MANAGE", ScopeRef.of(ScopeType.FACILITY, facilityId))) {
            throw new AccessDeniedException("Missing permission: THEATRE_MANAGE");
        }
    }
}
