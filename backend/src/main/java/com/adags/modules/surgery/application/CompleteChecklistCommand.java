package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.ChecklistPhase;

import java.util.List;
import java.util.UUID;

public record CompleteChecklistCommand(
        UUID surgeryOrderId,
        ChecklistPhase phase,
        List<String> items,
        Boolean countCorrect,
        String countDiscrepancyNote,
        boolean isEmergencyReduced,
        UUID completedBy
) {
}
