package com.adags.modules.identity.domain;

import java.util.UUID;

public record RoleSummary(UUID id, String code, String name) {
}
