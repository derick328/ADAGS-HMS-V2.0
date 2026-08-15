package com.adags.modules.identity.api;

import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.RoleAssignmentView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class MeController {

    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    public MeController(AuthorizationService authorizationService, UserRepository userRepository) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
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
}
