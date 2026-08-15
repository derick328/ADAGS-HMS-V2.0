package com.adags.shared.authz;

import java.util.Optional;

// The walk-up chain: WARD -> DEPARTMENT -> FACILITY. Resolution is always by
// id, never by name/code — that is what stops a ward-scoped user at one
// facility from resolving into a same-named ward at another.
public interface ScopeHierarchyPort {

    Optional<ScopeRef> parentOf(ScopeRef scope);
}
