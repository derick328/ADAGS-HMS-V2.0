package com.adags.shared.authz;

import java.util.UUID;

public record ScopeRef(ScopeType type, UUID id) {

    public ScopeRef {
        if (type == ScopeType.GLOBAL && id != null) {
            throw new IllegalArgumentException("GLOBAL scope must not carry an id");
        }
        if (type != ScopeType.GLOBAL && id == null) {
            throw new IllegalArgumentException(type + " scope requires an id");
        }
    }

    public static ScopeRef global() {
        return new ScopeRef(ScopeType.GLOBAL, null);
    }

    public static ScopeRef of(ScopeType type, UUID id) {
        return new ScopeRef(type, id);
    }
}
