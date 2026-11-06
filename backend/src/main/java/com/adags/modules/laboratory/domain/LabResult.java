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
        Instant createdAt,
        String resultType,
        String singleValue,
        String singleValueUnit,
        String overallInterpretation,
        String remarks,
        String labReportObjectKey
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
                machineOutputObjectKey, amendsId, amendmentReason, null,
                "NUMERIC", null, null, null, null, null);
    }
    public static LabResult create(UUID labOrderId, UUID specimenId, UUID enteredBy,
                                    VerificationLevel level, String methodology, String reagentsUsedNote,
                                    String findings, String interpretation, String referenceRangeNote, String conclusion,
                                    String machineOutputObjectKey, UUID amendsId, String amendmentReason,
                                    String resultType, String singleValue, String singleValueUnit,
                                    String overallInterpretation, String remarks, String labReportObjectKey) {
        LabResult base = create(labOrderId, specimenId, enteredBy, level, methodology, reagentsUsedNote,
                findings, interpretation, referenceRangeNote, conclusion, machineOutputObjectKey, amendsId, amendmentReason);
        return new LabResult(base.id(), base.labOrderId(), base.specimenId(), base.enteredBy(), base.enteredAt(),
                base.releasedAt(), base.verificationState(), base.verifiedBy(), base.verifiedAt(),
                base.verificationComment(), base.verificationLevelApplied(), base.resultVersionHash(),
                base.methodology(), base.reagentsUsedNote(), base.findings(), base.interpretation(),
                base.referenceRangeNote(), base.conclusion(), base.machineOutputObjectKey(), base.amendsId(),
                base.amendmentReason(), base.createdAt(), resultType == null ? "NUMERIC" : resultType,
                singleValue, singleValueUnit, overallInterpretation, remarks, labReportObjectKey);
    }
}
