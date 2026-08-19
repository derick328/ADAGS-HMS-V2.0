package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.WardStatus;

import java.util.UUID;

public record UpdateWardStatusCommand(UUID admissionId, WardStatus wardStatus, UUID actorUserId) {
}
