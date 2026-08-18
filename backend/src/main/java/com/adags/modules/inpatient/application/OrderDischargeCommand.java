package com.adags.modules.inpatient.application;

import java.util.UUID;

public record OrderDischargeCommand(UUID admissionId, UUID actorUserId) {
}
