package com.adags.support;

import com.adags.shared.authz.ScopeHierarchyPort;
import com.adags.shared.authz.ScopeRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FakeScopeHierarchyPort implements ScopeHierarchyPort {

    private final Map<ScopeRef, ScopeRef> parents = new HashMap<>();

    public void setParent(ScopeRef child, ScopeRef parent) {
        parents.put(child, parent);
    }

    @Override
    public Optional<ScopeRef> parentOf(ScopeRef scope) {
        return Optional.ofNullable(parents.get(scope));
    }
}
