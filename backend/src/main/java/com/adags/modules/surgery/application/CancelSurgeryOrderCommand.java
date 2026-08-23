package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.CancellationReason;

import java.util.UUID;

public record CancelSurgeryOrderCommand(UUID surgeryOrderId, CancellationReason reason, UUID actorUserId) {
}
