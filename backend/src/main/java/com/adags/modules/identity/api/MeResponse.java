package com.adags.modules.identity.api;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID userId,
        String username,
        boolean mustChangePassword,
        List<String> permissions,
        List<ScopeView> scopes
) {
}
