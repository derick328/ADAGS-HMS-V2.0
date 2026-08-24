package com.adags.modules.laboratory.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 15-lab-technician.md §10: a technician's proposal for a new catalogue
// test, mirroring PharmacyItemProposal's role for a new pharmacy item but
// a distinct table/type (V62's comment: different shape, different
// permission codes). Deliberately carries no verification_level/is_sensitive
// field — "set by CLINICAL_LEAD, not the proposing technician."
public record LabItemProposal(
        UUID id,
        String proposedName,
        String proposedClassification,
        Money proposedPrice,
        String notes,
        String specimenType,
        String containerType,
        int expectedTatHours,
        String justification,
        UUID proposedBy,
        Instant proposedAt,
        LabItemProposalStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        UUID createdLabTestId,
        List<LabItemProposalParameter> parameters
) {

    public static LabItemProposal propose(String proposedName, String proposedClassification, Money proposedPrice,
                                           String notes, String specimenType, String containerType,
                                           int expectedTatHours, String justification, UUID proposedBy,
                                           List<LabItemProposalParameter> parameters) {
        requireField(proposedName, "proposedName");
        requireField(proposedClassification, "proposedClassification");
        requireField(specimenType, "specimenType");
        requireField(containerType, "containerType");
        requireField(justification, "justification");
        if (proposedPrice == null) {
            throw new IllegalArgumentException("proposedPrice is required.");
        }
        if (expectedTatHours <= 0) {
            throw new IllegalArgumentException("expectedTatHours must be > 0.");
        }
        return new LabItemProposal(null, proposedName, proposedClassification, proposedPrice, notes, specimenType,
                containerType, expectedTatHours, justification, proposedBy, null, LabItemProposalStatus.PROPOSED,
                null, null, null, null, parameters);
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
    }
}
