package com.adags.modules.identity.application;

import java.util.UUID;

public record ChangeOwnPasswordCommand(UUID userId, String currentPassword, String newPassword) {
}
