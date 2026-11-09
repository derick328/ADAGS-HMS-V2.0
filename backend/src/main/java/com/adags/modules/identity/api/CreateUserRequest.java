package com.adags.modules.identity.api;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(@NotBlank String username, @NotBlank String password, java.util.List<java.util.UUID> unitIds, java.util.UUID primaryUnitId, java.util.UUID hodUnitId) {
}
