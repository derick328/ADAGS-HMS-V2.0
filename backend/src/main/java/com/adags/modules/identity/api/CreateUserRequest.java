package com.adags.modules.identity.api;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(@NotBlank String username, @NotBlank String temporaryPassword) {
}
