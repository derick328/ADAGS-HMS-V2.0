package com.adags.modules.identity.application;

import com.adags.shared.security.IssuedSession;

public record LoginResult(IssuedSession session, boolean mustChangePassword) {
}
