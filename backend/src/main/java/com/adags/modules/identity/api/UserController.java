package com.adags.modules.identity.api;

import com.adags.modules.identity.application.CreateUserCommand;
import com.adags.modules.identity.application.CreateUserService;
import com.adags.modules.identity.application.EndRoleAssignmentService;
import com.adags.modules.identity.application.GrantRoleAssignmentCommand;
import com.adags.modules.identity.application.GrantRoleAssignmentService;
import com.adags.modules.identity.domain.RoleAssignmentRepository;
import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 18-admin.md §4: user accounts and their scoped, time-bounded role
// assignments. USER_MANAGE creates/lists accounts; ROLE_ASSIGN grants and
// ends assignments — both already ADMIN's in the V5 permission catalogue.
// Deactivation (§13, checking for open work before disabling a user) is not
// in this slice.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final CreateUserService createUserService;
    private final GrantRoleAssignmentService grantRoleAssignmentService;
    private final EndRoleAssignmentService endRoleAssignmentService;
    private final UserRepository userRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AuthorizationService authorizationService;

    public UserController(
            CreateUserService createUserService,
            GrantRoleAssignmentService grantRoleAssignmentService,
            EndRoleAssignmentService endRoleAssignmentService,
            UserRepository userRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AuthorizationService authorizationService
    ) {
        this.createUserService = createUserService;
        this.grantRoleAssignmentService = grantRoleAssignmentService;
        this.endRoleAssignmentService = endRoleAssignmentService;
        this.userRepository = userRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "USER_MANAGE");
        return userRepository.findAllSummaries().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "USER_MANAGE");
        User user = createUserService.create(new CreateUserCommand(request.username(), request.temporaryPassword(), actor.userId()));
        return UserResponse.from(user);
    }

    @GetMapping("/{id}/role-assignments")
    public List<RoleAssignmentResponse> roleAssignments(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "ROLE_ASSIGN");
        return roleAssignmentRepository.findByUserId(id).stream().map(RoleAssignmentResponse::from).toList();
    }

    @PostMapping("/{id}/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleAssignmentResponse grantRoleAssignment(
            @PathVariable UUID id, @Valid @RequestBody GrantRoleAssignmentRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "ROLE_ASSIGN");
        var record = grantRoleAssignmentService.grant(new GrantRoleAssignmentCommand(
                id, request.roleCode(), request.scopeType(), request.scopeId(), request.validFrom(), request.validTo(), actor.userId()));
        return RoleAssignmentResponse.from(record);
    }

    @PostMapping("/{id}/role-assignments/{assignmentId}/end")
    public void endRoleAssignment(
            @PathVariable UUID id, @PathVariable UUID assignmentId, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "ROLE_ASSIGN");
        endRoleAssignmentService.end(assignmentId);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
