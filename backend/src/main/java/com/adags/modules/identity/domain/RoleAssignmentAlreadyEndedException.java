package com.adags.modules.identity.domain;

import java.util.UUID;

public class RoleAssignmentAlreadyEndedException extends RuntimeException {

    public RoleAssignmentAlreadyEndedException(UUID assignmentId) {
        super("Role assignment " + assignmentId + " has already ended.");
    }
}
