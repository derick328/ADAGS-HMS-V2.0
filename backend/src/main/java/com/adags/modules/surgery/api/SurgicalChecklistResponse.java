package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.ChecklistPhase;
import com.adags.modules.surgery.domain.SurgicalChecklist;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SurgicalChecklistResponse(
        UUID id,
        UUID surgeryOrderId,
        ChecklistPhase phase,
        List<String> items,
        UUID completedBy,
        Instant completedAt,
        Boolean countCorrect,
        String countDiscrepancyNote,
        boolean isEmergencyReduced
) {
    public static SurgicalChecklistResponse from(SurgicalChecklist checklist) {
        return new SurgicalChecklistResponse(checklist.id(), checklist.surgeryOrderId(), checklist.phase(),
                checklist.items(), checklist.completedBy(), checklist.completedAt(), checklist.countCorrect(),
                checklist.countDiscrepancyNote(), checklist.isEmergencyReduced());
    }
}
