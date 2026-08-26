package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.CreateWardCommand;
import com.adags.modules.inpatient.application.CreateWardService;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.Money;
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

import java.util.NoSuchElementException;
import java.util.UUID;

// 17-ward-nurse.md §8.1: ward inventory is administrative setup, defined by
// WARD_BED_MANAGE (18-admin.md's catalogue), never by the nurse who
// executes against it.
@RestController
@RequestMapping("/api/wards")
public class WardController {

    private final CreateWardService createWardService;
    private final WardRepository wardRepository;
    private final AuthorizationService authorizationService;

    public WardController(
            CreateWardService createWardService, WardRepository wardRepository, AuthorizationService authorizationService
    ) {
        this.createWardService = createWardService;
        this.wardRepository = wardRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WardResponse create(@Valid @RequestBody CreateWardRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "WARD_BED_MANAGE", ScopeType.DEPARTMENT, request.departmentId());
        CreateWardCommand command = new CreateWardCommand(request.departmentId(), request.code(), request.name(),
                request.wardClass(), request.genderRestriction(),
                new Money(request.dailyRateMinor(), request.currencyCode()));
        Ward ward = createWardService.create(command);
        return WardResponse.from(ward);
    }

    // 17-ward-nurse.md §3: resolves the ward's own name/rate for the shell
    // header and Ward Bills — the nurse already has wardId from /me's
    // WARD-scoped RoleAssignment, this fills in the rest.
    @GetMapping("/{id}")
    public WardResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "WARD_PATIENT_READ", ScopeType.WARD, id);
        Ward ward = wardRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Ward not found: " + id));
        return WardResponse.from(ward);
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeType scopeType, UUID scopeId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(scopeType, scopeId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
