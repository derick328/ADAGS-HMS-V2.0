package com.adags.modules.identity.api;

import jakarta.validation.constraints.NotBlank;

public record SetUserPasswordRequest(@NotBlank String password) {
}
