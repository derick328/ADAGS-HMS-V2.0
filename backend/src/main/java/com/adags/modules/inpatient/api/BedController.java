package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.CreateBedCommand;
import com.adags.modules.inpatient.application.CreateBedService;
import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedRepository;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wards/{wardId}/beds")
public class BedController {

    private final CreateBedService createBedService;
    private final BedRepository bedRepository;
    private final AuthorizationService authorizationService;

    public BedController(CreateBedService createBedService, BedRepository bedRepository, AuthorizationService authorizationService) {
        this.createBedService = createBedService;
        this.bedRepository = bedRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BedResponse create(
            @PathVariable UUID wardId, @Valid @RequestBody CreateBedRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "WARD_BED_MANAGE", ScopeType.WARD, wardId);
        Bed bed = createBedService.create(new CreateBedCommand(wardId, request.label(), request.bedType()));
        return BedResponse.from(bed);
    }

    // 17-ward-nurse.md §8.1: the list a nurse actually picks a bed from —
    // within the ordered class, so this is scoped to one ward at a time.
    @GetMapping("/free")
    public List<BedResponse> free(@PathVariable UUID wardId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "BED_ASSIGN", ScopeType.WARD, wardId);
        return bedRepository.findFreeByWardId(wardId).stream().map(BedResponse::from).toList();
    }

    // Screen 3's bed map — every bed in the ward, occupied or not, so the
    // nurse can see the whole ward's layout, not just what's free.
    @GetMapping
    public List<BedResponse> list(@PathVariable UUID wardId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "WARD_PATIENT_READ", ScopeType.WARD, wardId);
        return bedRepository.findByWardId(wardId).stream().map(BedResponse::from).toList();
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeType scopeType, UUID scopeId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(scopeType, scopeId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
