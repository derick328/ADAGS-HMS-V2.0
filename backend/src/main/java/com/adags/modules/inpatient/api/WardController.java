package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.CreateWardCommand;
import com.adags.modules.inpatient.application.CreateWardService;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 17-ward-nurse.md §8.1: ward inventory is administrative setup, defined by
// WARD_BED_MANAGE (18-admin.md's catalogue), never by the nurse who
// executes against it.
@RestController
@RequestMapping("/api/wards")
public class WardController {

    private final CreateWardService createWardService;
    private final AuthorizationService authorizationService;

    public WardController(CreateWardService createWardService, AuthorizationService authorizationService) {
        this.createWardService = createWardService;
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

    private void requirePermission(Actor actor, String permissionCode, ScopeType scopeType, UUID scopeId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(scopeType, scopeId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
