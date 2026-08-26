package com.adags.modules.laboratory.application;

import java.util.List;
import java.util.UUID;

public record ProposeLabItemCommand(
        String proposedName,
        String proposedClassification,
        long proposedPriceMinor,
        String notes,
        String specimenType,
        String containerType,
        int expectedTatHours,
        String justification,
        UUID proposedBy,
        List<ProposedParameter> parameters
) {
    public record ProposedParameter(String name, String unit, String method) {
    }
}
