package com.adags.modules.surgery.application;

import java.util.UUID;

public record RevokeConsentCommand(UUID surgeryOrderId, UUID actorUserId) {
}
