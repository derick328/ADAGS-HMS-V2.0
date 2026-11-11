package com.adags.shared.authz;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Plain object, no Spring annotations, no direct I/O — everything it needs
// comes through the ports, so this is unit-testable with fakes and no
// Spring context. See docs/spec/01-architecture.md §8.1 for the formula this implements:
//   allow = hasPermission AND withinScope AND (hasCareRelationship OR breakGlass)
public class AuthorizationService {

    private final RoleAssignmentPort roleAssignmentPort;
    private final ScopeHierarchyPort scopeHierarchyPort;
    private final CareRelationshipPort careRelationshipPort;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public AuthorizationService(
            RoleAssignmentPort roleAssignmentPort,
            ScopeHierarchyPort scopeHierarchyPort,
            CareRelationshipPort careRelationshipPort,
            AuditRecorder auditRecorder,
            Clock clock
    ) {
        this.roleAssignmentPort = roleAssignmentPort;
        this.scopeHierarchyPort = scopeHierarchyPort;
        this.careRelationshipPort = careRelationshipPort;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    public boolean hasPermission(Actor actor, String permissionCode) {
        return activeAssignments(actor).stream()
                .anyMatch(assignment -> assignment.permissionCodes().contains(permissionCode));
    }

    public boolean hasPermission(Actor actor, String permissionCode, ScopeRef targetScope) {
        return activeAssignments(actor).stream()
                .filter(assignment -> assignment.permissionCodes().contains(permissionCode))
                .anyMatch(assignment -> withinScope(assignment.scope(), targetScope));
    }

    /** A pending encounter has no department yet; staff assigned within its facility may act on it. */
    public boolean hasPermissionForPendingEncounter(Actor actor, String permissionCode, UUID facilityId) {
        ScopeRef facility = ScopeRef.of(ScopeType.FACILITY, facilityId);
        return activeAssignments(actor).stream()
                .filter(assignment -> assignment.permissionCodes().contains(permissionCode))
                .anyMatch(assignment -> assignment.scope().type() == ScopeType.GLOBAL
                        || withinScope(assignment.scope(), facility)
                        || withinScope(facility, assignment.scope()));
    }

    /**
     * True if the actor holds ANY of the given codes, unscoped — several
     * screens (a doctor's queue, request history) are shared by roles that
     * each hold a different one of a short list of codes rather than all of
     * them (see EncounterController#search's own comment for why). Kept
     * here rather than duplicated per-controller as a private loop — this
     * was previously a copy-pasted `for (code : codes) if (hasPermission)`
     * in several REST controllers.
     */
    public boolean hasAnyPermission(Actor actor, String... permissionCodes) {
        for (String code : permissionCodes) {
            if (hasPermission(actor, code)) {
                return true;
            }
        }
        return false;
    }

    /** Scoped counterpart of {@link #hasAnyPermission(Actor, String...)} — any of the codes, checked against one target scope. */
    public boolean hasAnyPermission(Actor actor, ScopeRef targetScope, String... permissionCodes) {
        for (String code : permissionCodes) {
            if (hasPermission(actor, code, targetScope)) {
                return true;
            }
        }
        return false;
    }

    public PolicyResult checkPatientAccess(Actor actor, String permissionCode, UUID patientId, ScopeRef targetScope) {
        if (!hasPermission(actor, permissionCode, targetScope)) {
            return PolicyResult.DENIED;
        }
        if (careRelationshipPort.hasCareRelationship(actor.userId(), patientId)) {
            return PolicyResult.ALLOWED;
        }
        return PolicyResult.BREAK_GLASS_REQUIRED;
    }

    public void recordBreakGlass(Actor actor, UUID patientId, String resourceType, UUID resourceId, String justification) {
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("Break-glass access requires a typed justification.");
        }
        auditRecorder.recordBreakGlass(actor.userId(), patientId, resourceType, resourceId, justification);
    }

    public List<RoleAssignmentView> activeAssignments(Actor actor) {
        Instant now = clock.instant();
        return roleAssignmentPort.findAssignments(actor.userId()).stream()
                .filter(assignment -> assignment.isActiveAt(now))
                .toList();
    }

    private boolean withinScope(ScopeRef assignmentScope, ScopeRef targetScope) {
        if (assignmentScope.type() == ScopeType.GLOBAL) {
            return true;
        }
        ScopeRef current = targetScope;
        while (current != null) {
            if (current.equals(assignmentScope)) {
                return true;
            }
            current = scopeHierarchyPort.parentOf(current).orElse(null);
        }
        return false;
    }
}
