package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.UUID;

// 16-lab-verification-model.md v0.2 §2: release (enteredBy/enteredAt/
// releasedAt) and verification (verificationState/verifiedBy/verifiedAt/
// verificationComment/verificationLevelApplied/resultVersionHash) are
// independent axes on one row, not stages of one status.
// verificationLevelApplied is snapshotted from LabTest.verificationLevel
// at entry — never read live afterwards.
public record LabResult(
        UUID id,
        UUID labOrderId,
        UUID specimenId,
        UUID enteredBy,
        Instant enteredAt,
        Instant releasedAt,
        VerificationState verificationState,
        UUID verifiedBy,
        Instant verifiedAt,
        String verificationComment,
        VerificationLevel verificationLevelApplied,
        String resultVersionHash,
        String methodology,
        String reagentsUsedNote,
        String findings,
        String interpretation,
        String referenceRangeNote,
        String conclusion,
        String machineOutputObjectKey,
        UUID amendsId,
        String amendmentReason,
        Instant createdAt
) {

    public static LabResult create(UUID labOrderId, UUID specimenId, UUID enteredBy,
                                    VerificationLevel verificationLevelApplied,
                                    String methodology, String reagentsUsedNote, String findings,
                                    String interpretation, String referenceRangeNote, String conclusion,
                                    String machineOutputObjectKey, UUID amendsId, String amendmentReason) {
        VerificationState initialState =
                verificationLevelApplied == VerificationLevel.NONE ? VerificationState.NOT_REQUIRED : VerificationState.PENDING;
        return new LabResult(null, labOrderId, specimenId, enteredBy, null, null,
                initialState, null, null, null, verificationLevelApplied, null,
                methodology, reagentsUsedNote, findings, interpretation, referenceRangeNote, conclusion,
                machineOutputObjectKey, amendsId, amendmentReason, null);
    }
}
