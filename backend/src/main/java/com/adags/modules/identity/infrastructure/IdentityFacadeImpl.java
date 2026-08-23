package com.adags.modules.identity.infrastructure;

import com.adags.modules.identity.IdentityFacade;
import com.adags.shared.kernel.UserRef;
import com.adags.modules.identity.domain.RoleAssignmentRecord;
import com.adags.modules.identity.domain.RoleAssignmentRepository;
import com.adags.modules.identity.domain.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdentityFacadeImpl implements IdentityFacade {

    private final UserRepository userRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final Clock clock;

    public IdentityFacadeImpl(UserRepository userRepository, RoleAssignmentRepository roleAssignmentRepository, Clock clock) {
        this.userRepository = userRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.clock = clock;
    }

    @Override
    public Optional<String> findUsername(UUID userId) {
        return userRepository.findById(userId).map(user -> user.username());
    }

    @Override
    public List<UserRef> findActiveUsersWithRole(String roleCode) {
        // A handful of rows (a facility's doctor roster) — one lookup per
        // holder rather than a new joined query in JdbcRoleAssignmentRepository.
        List<RoleAssignmentRecord> holders = roleAssignmentRepository.findActiveByRoleCode(roleCode, clock.instant());
        return holders.stream()
                .map(RoleAssignmentRecord::userId)
                .distinct()
                .flatMap(userId -> userRepository.findById(userId).stream())
                .map(user -> new UserRef(user.id(), user.username()))
                .toList();
    }
}
