package com.adags.modules.encounters.application;

import com.adags.modules.encounters.domain.DispositionOutcome;

import java.util.UUID;

public record FinalizeConsultationCommand(
        UUID encounterId,
        UUID doctorId,
        DispositionOutcome disposition,
        boolean retrospectiveCompletion,
        String retrospectiveNote
) {
}
