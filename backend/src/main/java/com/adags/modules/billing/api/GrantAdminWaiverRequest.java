package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantAdminWaiverRequest(
        @NotNull UUID encounterId,
        @NotBlank String reason
) {
}
