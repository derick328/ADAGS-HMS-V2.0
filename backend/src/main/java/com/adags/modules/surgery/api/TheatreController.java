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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
        requirePermission(actor, "THEATRE_MANAGE", ScopeType.FACILITY, request.facilityId());
        Theatre theatre = createTheatreService.create(new CreateTheatreCommand(request.facilityId(), request.name()));
        return TheatreResponse.from(theatre);
    }

    // Scheduling needs the list to offer a theatre picker — read access
    // follows the same scope as creation, since nothing narrower is
    // defined for it yet.
    @GetMapping
    public List<TheatreResponse> listByFacility(@RequestParam UUID facilityId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "SURGERY_ORDER_CREATE", ScopeType.FACILITY, facilityId);
        return theatreRepository.findByFacilityId(facilityId).stream().map(TheatreResponse::from).toList();
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeType scopeType, UUID scopeId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(scopeType, scopeId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
