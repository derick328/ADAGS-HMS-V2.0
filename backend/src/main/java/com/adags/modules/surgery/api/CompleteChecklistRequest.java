package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.ChecklistPhase;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CompleteChecklistRequest(
        @NotNull UUID surgeryOrderId,
        @NotNull ChecklistPhase phase,
        List<String> items,
        Boolean countCorrect,
        String countDiscrepancyNote,
        boolean isEmergencyReduced
) {
}
