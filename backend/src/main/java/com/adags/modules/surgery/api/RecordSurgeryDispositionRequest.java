package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.SurgeryDisposition;
import jakarta.validation.constraints.NotNull;

public record RecordSurgeryDispositionRequest(
        @NotNull SurgeryDisposition disposition,
        /** Required, and only meaningful, for ADMIT_TO_WARD. */
        String orderedWardClass
) {
}
