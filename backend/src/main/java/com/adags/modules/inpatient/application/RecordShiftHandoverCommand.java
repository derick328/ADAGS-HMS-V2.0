package com.adags.modules.inpatient.application;

import java.util.List;
import java.util.UUID;

public record RecordShiftHandoverCommand(
        UUID admissionId,
        UUID wardId,
        UUID shiftFrom,
        UUID shiftTo,
        String situation,
        String background,
        String assessment,
        String recommendation,
        /** Nurse-added tasks, appended after the system's pre-populated ones. */
        List<String> additionalOutstandingTasks,
        UUID outgoingNurseId
) {
}
