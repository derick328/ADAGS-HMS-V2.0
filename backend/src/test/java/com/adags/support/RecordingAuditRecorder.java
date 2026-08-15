package com.adags.support;

import com.adags.shared.authz.AuditRecorder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecordingAuditRecorder implements AuditRecorder {

    public record Recorded(UUID actorUserId, UUID patientId, String resourceType, UUID resourceId, String justification) {
    }

    public record RecordedEntry(UUID actorUserId, String action, String resourceType, UUID resourceId, UUID patientId) {
    }

    public final List<Recorded> recorded = new ArrayList<>();
    public final List<RecordedEntry> recordedEntries = new ArrayList<>();

    @Override
    public void recordBreakGlass(UUID actorUserId, UUID patientId, String resourceType, UUID resourceId, String justification) {
        recorded.add(new Recorded(actorUserId, patientId, resourceType, resourceId, justification));
    }

    @Override
    public void record(UUID actorUserId, String action, String resourceType, UUID resourceId, UUID patientId) {
        recordedEntries.add(new RecordedEntry(actorUserId, action, resourceType, resourceId, patientId));
    }
}
