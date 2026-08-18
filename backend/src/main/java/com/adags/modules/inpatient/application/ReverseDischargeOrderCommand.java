package com.adags.modules.inpatient.application;

import java.util.UUID;

public record ReverseDischargeOrderCommand(UUID admissionId, String reason, UUID actorUserId) {
}
