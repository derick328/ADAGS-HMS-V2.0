package com.adags.modules.identity.api;

import com.adags.modules.identity.domain.RoleSummary;

import java.util.UUID;

public record RoleResponse(UUID id, String code, String name) {

    public static RoleResponse from(RoleSummary summary) {
        return new RoleResponse(summary.id(), summary.code(), summary.name());
    }
}
