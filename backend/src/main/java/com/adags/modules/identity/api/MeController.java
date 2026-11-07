package com.adags.modules.identity.api;

import com.adags.modules.identity.application.ChangeOwnPasswordCommand;
import com.adags.modules.identity.application.ChangeOwnPasswordService;
import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.RoleAssignmentView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class MeController {

    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final ChangeOwnPasswordService changeOwnPasswordService;

    public MeController(
            AuthorizationService authorizationService,
            UserRepository userRepository,
            ChangeOwnPasswordService changeOwnPasswordService
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.changeOwnPasswordService = changeOwnPasswordService;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Actor actor) {
        User user = userRepository.findById(actor.userId()).orElseThrow(NoSuchElementException::new);
        List<RoleAssignmentView> assignments = authorizationService.activeAssignments(actor);

        List<String> permissions = assignments.stream()
                .flatMap(assignment -> assignment.permissionCodes().stream())
                .distinct()
                .sorted()
                .toList();

        List<ScopeView> scopes = assignments.stream()
                .map(assignment -> new ScopeView(
                        assignment.roleCode(),
                        assignment.scope().type().name(),
                        assignment.scope().id(),
                        assignment.validFrom(),
                        assignment.validTo()))
                .toList();

        return new MeResponse(user.id(), user.username(), user.mustChangePassword(), permissions, scopes);
    }

    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "USER_MANAGE")) {
            throw new AccessDeniedException("Only admins can change passwords.");
        }
        changeOwnPasswordService.changePassword(
                new ChangeOwnPasswordCommand(actor.userId(), request.currentPassword(), request.newPassword()));
    }
}
