package com.adags.modules.inpatient.application;

import java.time.Instant;
import java.util.UUID;

// actualDepartureAt is nullable — null means "now," letting a nurse
// confirming departure in real time not have to type a timestamp; a
// backdated confirmation (recording a departure that already happened)
// supplies it explicitly.
public record CompleteDischargeCommand(UUID admissionId, Instant actualDepartureAt, UUID actorUserId) {
}
