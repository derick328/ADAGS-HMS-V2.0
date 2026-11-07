package com.adags.modules.identity.application;

import java.util.UUID;

public record CreateUserCommand(String username, String password, UUID createdBy) {
}
