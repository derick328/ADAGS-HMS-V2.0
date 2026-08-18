package com.adags.modules.pharmacy.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §5.1: pharmacist proposes an equivalent, doctor
// approves or rejects. replacementLineId is set only on approval — the
// new PrescriptionLine created for proposedItemId.
public record SubstitutionProposal(
        UUID id,
        UUID prescriptionLineId,
        UUID proposedItemId,
        String reason,
        UUID proposedBy,
        Instant proposedAt,
        SubstitutionStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        UUID replacementLineId
) {

    public static SubstitutionProposal propose(UUID prescriptionLineId, UUID proposedItemId, String reason, UUID proposedBy) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required.");
        }
        return new SubstitutionProposal(null, prescriptionLineId, proposedItemId, reason, proposedBy, null,
                SubstitutionStatus.PROPOSED, null, null, null, null);
    }
}
