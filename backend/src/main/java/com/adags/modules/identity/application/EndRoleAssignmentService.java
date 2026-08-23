package com.adags.modules.identity.application;

import com.adags.modules.identity.domain.RoleAssignmentAlreadyEndedException;
import com.adags.modules.identity.domain.RoleAssignmentRecord;
import com.adags.modules.identity.domain.RoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EndRoleAssignmentService {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final Clock clock;

    public EndRoleAssignmentService(RoleAssignmentRepository roleAssignmentRepository, Clock clock) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.clock = clock;
    }

    @Transactional
    public void end(UUID assignmentId) {
        RoleAssignmentRecord assignment = roleAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NoSuchElementException("Role assignment not found: " + assignmentId));
        if (assignment.revokedAt() != null) {
            throw new RoleAssignmentAlreadyEndedException(assignmentId);
        }
        roleAssignmentRepository.revoke(assignmentId, clock.instant());
    }
}
