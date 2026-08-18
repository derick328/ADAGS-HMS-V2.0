package com.adags.modules.clinical.application;

import java.util.UUID;

public record AutosaveConsultationRecordCommand(
        UUID consultationRecordId,
        String historyOfPresentingIllness,
        String pastMedicalHistory,
        String pastSurgicalHistory,
        String familySocialHistory,
        String physicalExamination,
        String clinicalNotes,
        String treatmentPlan
) {
}
