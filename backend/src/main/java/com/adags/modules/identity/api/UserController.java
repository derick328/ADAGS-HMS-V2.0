package com.adags.modules.identity.api;

import com.adags.modules.encounters.DepartmentFacade;
import com.adags.modules.identity.application.CreateUserCommand;
import com.adags.modules.identity.application.CreateUserService;
import com.adags.modules.identity.application.EndRoleAssignmentService;
import com.adags.modules.identity.application.GrantRoleAssignmentCommand;
import com.adags.modules.identity.application.GrantRoleAssignmentService;
import com.adags.modules.identity.application.SetUserPasswordService;
import com.adags.modules.identity.domain.RoleAssignmentRepository;
import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
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
    private final SetUserPasswordService setUserPasswordService;
    private final GrantRoleAssignmentService grantRoleAssignmentService;
    private final EndRoleAssignmentService endRoleAssignmentService;
    private final UserRepository userRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AuthorizationService authorizationService;
    private final DepartmentFacade departments;

    public UserController(
            CreateUserService createUserService,
            SetUserPasswordService setUserPasswordService,
            GrantRoleAssignmentService grantRoleAssignmentService,
            EndRoleAssignmentService endRoleAssignmentService,
            UserRepository userRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AuthorizationService authorizationService,
            DepartmentFacade departments
    ) {
        this.createUserService = createUserService;
        this.setUserPasswordService = setUserPasswordService;
        this.grantRoleAssignmentService = grantRoleAssignmentService;
        this.endRoleAssignmentService = endRoleAssignmentService;
        this.userRepository = userRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.authorizationService = authorizationService;
        this.departments = departments;
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "USER_MANAGE");
        return userRepository.findAllSummaries().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "USER_MANAGE");
        User user = createUserService.create(new CreateUserCommand(request.username(), request.password(), actor.userId()));
        if (request.unitIds() != null) {
            requireClinicScope(actor,request.unitIds());
            departments.assign(user.id(), request.unitIds(), request.primaryUnitId(), actor.userId());
            if (request.hodUnitId() != null) {
                if (!request.unitIds().contains(request.hodUnitId())) throw new IllegalArgumentException("HOD clinic must be assigned.");
                departments.appoint(request.hodUnitId(), user.id(), actor.userId());
            }
        }
        return UserResponse.from(user);
    }

    public record ClinicAssignmentsRequest(List<UUID> unitIds, UUID primaryUnitId, UUID hodUnitId, List<UUID> vacateHodUnitIds) {}

    @GetMapping("/{id}/clinic-assignments")
    public List<DepartmentFacade.Assignment> clinicAssignments(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "USER_MANAGE");
        return departments.assignments(id);
    }

    @PutMapping("/{id}/clinic-assignments")
    @org.springframework.transaction.annotation.Transactional
    public void updateClinicAssignments(@PathVariable UUID id, @RequestBody ClinicAssignmentsRequest request,
                                        @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "USER_MANAGE");
        requireClinicScope(actor,request.unitIds());
        var existing = departments.assignments(id);
        requireClinicScope(actor,existing.stream().map(DepartmentFacade.Assignment::departmentId).toList());
        if (request.vacateHodUnitIds() != null) {
            for (UUID unitId : request.vacateHodUnitIds()) {
                if (existing.stream().noneMatch(a -> a.departmentId().equals(unitId) && a.hod()))
                    throw new IllegalArgumentException("Only this user's active HOD appointment can be vacated here.");
                departments.vacateForUser(unitId,id,actor.userId());
            }
        }
        departments.assign(id, request.unitIds(), request.primaryUnitId(), actor.userId());
        if (request.hodUnitId() != null) {
            if (request.unitIds() == null || !request.unitIds().contains(request.hodUnitId()))
                throw new IllegalArgumentException("HOD clinic must be assigned to the user.");
            departments.appoint(request.hodUnitId(), id, actor.userId());
        }
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@PathVariable UUID id, @Valid @RequestBody SetUserPasswordRequest request,
                            @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "USER_MANAGE");
        setUserPasswordService.setPassword(id, request.password());
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

    private void requireClinicScope(Actor actor, List<UUID> unitIds) {
        if (unitIds == null) return;
        for (UUID unitId : unitIds) {
            if (!authorizationService.hasPermission(actor,"USER_MANAGE",
                    ScopeRef.of(ScopeType.FACILITY,departments.facilityId(unitId))))
                throw new AccessDeniedException("Missing facility department management permission.");
        }
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
