package com.adags.shared.authz;

import java.util.UUID;

public interface AuditRecorder {

    void recordBreakGlass(UUID actorUserId, UUID patientId, String resourceType, UUID resourceId, String justification);
}
