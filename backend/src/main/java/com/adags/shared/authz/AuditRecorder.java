package com.adags.shared.authz;

import java.util.UUID;

public interface AuditRecorder {

    void recordBreakGlass(UUID actorUserId, UUID patientId, String resourceType, UUID resourceId, String justification);

    /** Ordinary (non-break-glass) audit entry — {@code resourceId} and {@code patientId} may both be null. */
    void record(UUID actorUserId, String action, String resourceType, UUID resourceId, UUID patientId);
}
