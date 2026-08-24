package com.adags.modules.laboratory.domain;

import java.util.UUID;

// 15-lab-technician.md §10: a proposed test's parameter list — same shape
// as LabTestParameter's core fields (name/unit/method), before it exists
// as a real catalogue row. Empty is valid: "a test approved without
// parameters generates an empty result form."
public record LabItemProposalParameter(
        UUID id,
        UUID proposalId,
        String name,
        String unit,
        String method,
        int sortOrder
) {
    public static LabItemProposalParameter create(String name, String unit, String method, int sortOrder) {
        return new LabItemProposalParameter(null, null, name, unit, method, sortOrder);
    }
}
