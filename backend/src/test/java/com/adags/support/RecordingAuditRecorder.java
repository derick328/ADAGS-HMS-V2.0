package com.adags.support;

import com.adags.shared.authz.AuditRecorder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecordingAuditRecorder implements AuditRecorder {

    public record Recorded(UUID actorUserId, UUID patientId, String resourceType, UUID resourceId, String justification) {
    }

    public final List<Recorded> recorded = new ArrayList<>();

    @Override
    public void recordBreakGlass(UUID actorUserId, UUID patientId, String resourceType, UUID resourceId, String justification) {
        recorded.add(new Recorded(actorUserId, patientId, resourceType, resourceId, justification));
    }
}
