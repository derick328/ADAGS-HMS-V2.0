package com.adags.modules.identity.api;

import com.adags.modules.identity.domain.RoleRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The role catalogue backing the role-assignment picker on the Users screen.
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleRepository roleRepository;
    private final AuthorizationService authorizationService;

    public RoleController(RoleRepository roleRepository, AuthorizationService authorizationService) {
        this.roleRepository = roleRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<RoleResponse> list(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "ROLE_ASSIGN")) {
            throw new AccessDeniedException("Missing permission: ROLE_ASSIGN");
        }
        return roleRepository.findAll().stream().map(RoleResponse::from).toList();
    }
}
