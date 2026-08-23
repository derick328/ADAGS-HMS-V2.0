package com.adags.modules.surgery.application;

import java.util.UUID;

public record CompleteSurgeryOrderCommand(UUID surgeryOrderId, UUID actorUserId) {
}
