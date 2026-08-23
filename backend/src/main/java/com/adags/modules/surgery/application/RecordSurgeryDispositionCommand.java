package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.SurgeryDisposition;

import java.util.UUID;

public record RecordSurgeryDispositionCommand(
        UUID surgeryOrderId,
        SurgeryDisposition disposition,
        /** Required, and only meaningful, for ADMIT_TO_WARD — the raw WardClass enum name (GENERAL/PRIVATE). */
        String orderedWardClass,
        UUID actorUserId
) {
}
