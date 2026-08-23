package com.adags.modules.surgery.application;

import java.util.UUID;

public record StartSurgeryOrderCommand(UUID surgeryOrderId, UUID actorUserId) {
}
