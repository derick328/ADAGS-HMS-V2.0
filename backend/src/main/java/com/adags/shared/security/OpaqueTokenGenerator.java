package com.adags.shared.security;

import java.security.SecureRandom;
import java.util.Base64;

final class OpaqueTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokenGenerator() {
    }

    static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
