package com.adags.modules.identity.application;

import com.adags.modules.identity.domain.RoleAssignmentRecord;
import com.adags.modules.identity.domain.RoleAssignmentRepository;
import com.adags.modules.identity.domain.RoleRepository;
import com.adags.modules.identity.domain.RoleSummary;
import com.adags.modules.identity.domain.SelfRoleGrantNotAllowedException;
import com.adags.modules.identity.domain.UserRepository;
import com.adags.shared.authz.ScopeRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;

// 18-admin.md §4: "The UI shows a user's assignments as a list with add and
// end-date actions." §11's self-elevation floor (SelfRoleGrantNotAllowedException,
// backed by the V57 DB CHECK) applies to every grant, not just ADMIN — nobody
// grants themselves a role, full stop; that is the simple invariant this
// slice enforces, ahead of the fuller second-admin-approval elevation
// workflow §11 also describes.
@Service
public class GrantRoleAssignmentService {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public GrantRoleAssignmentService(
            RoleAssignmentRepository roleAssignmentRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public RoleAssignmentRecord grant(GrantRoleAssignmentCommand command) {
        if (command.grantedBy().equals(command.userId())) {
            throw new SelfRoleGrantNotAllowedException();
        }
        userRepository.findById(command.userId())
                .orElseThrow(() -> new NoSuchElementException("User not found: " + command.userId()));
        RoleSummary role = roleRepository.findByCode(command.roleCode())
                .orElseThrow(() -> new NoSuchElementException("Role not found: " + command.roleCode()));

        // Reuses ScopeRef's own invariant — GLOBAL carries no id, every other
        // scope type requires one — rather than re-deriving it here.
        ScopeRef.of(command.scopeType(), command.scopeId());

        Instant validFrom = command.validFrom() != null ? command.validFrom() : clock.instant();
        if (command.validTo() != null && !command.validTo().isAfter(validFrom)) {
            throw new IllegalArgumentException("valid_to must be after valid_from.");
        }

        return roleAssignmentRepository.grant(command.userId(), role.id(), command.scopeType(), command.scopeId(),
                validFrom, command.validTo(), command.grantedBy());
    }
}
