package com.adags.modules.surgery.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTheatreRequest(
        @NotNull UUID facilityId,
        @NotNull String name
) {
}
