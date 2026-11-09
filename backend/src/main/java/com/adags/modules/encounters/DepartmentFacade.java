package com.adags.modules.encounters;

import java.util.List;
import java.util.UUID;

/** Organizational assignments exposed to user management without clinical record access. */
public interface DepartmentFacade {
    record Assignment(UUID departmentId, boolean primary, boolean hod) {}
    List<Assignment> assignments(UUID userId);
    UUID facilityId(UUID unitId);
    void assign(UUID userId, List<UUID> unitIds, UUID primaryId, UUID actorId);
    void appoint(UUID unitId, UUID userId, UUID actorId);
    void vacateForUser(UUID unitId, UUID userId, UUID actorId);
}
