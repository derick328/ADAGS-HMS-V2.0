package com.adags.shared.authz;

import com.adags.support.FakeCareRelationshipPort;
import com.adags.support.FakeRoleAssignmentPort;
import com.adags.support.FakeScopeHierarchyPort;
import com.adags.support.RecordingAuditRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Pure JUnit, no Spring context — AuthorizationService takes its ports as
// plain interfaces so this exercises the policy logic in isolation.
class AuthorizationServiceTest {

    private static final String PERMISSION = "CLINICAL_NOTE_READ";
    private static final Instant NOW = Instant.parse("2026-08-14T09:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeRoleAssignmentPort roleAssignmentPort = new FakeRoleAssignmentPort();
    private final FakeScopeHierarchyPort scopeHierarchyPort = new FakeScopeHierarchyPort();
    private final FakeCareRelationshipPort careRelationshipPort = new FakeCareRelationshipPort();
    private final RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();

    private final AuthorizationService service = new AuthorizationService(
            roleAssignmentPort, scopeHierarchyPort, careRelationshipPort, auditRecorder, clock);

    private final UUID actorUserId = UUID.randomUUID();
    private final Actor actor = new Actor(actorUserId);

    private RoleAssignmentView activeAssignment(ScopeRef scope, String... permissionCodes) {
        return new RoleAssignmentView(
                UUID.randomUUID(), "DOCTOR", scope, NOW.minusSeconds(3600), null, null, Set.of(permissionCodes));
    }

    @Nested
    class HasPermissionNoScope {

        @Test
        void grantsWhenAssignmentCarriesThePermission() {
            roleAssignmentPort.add(actorUserId, activeAssignment(ScopeRef.global(), PERMISSION));
            assertThat(service.hasPermission(actor, PERMISSION)).isTrue();
        }

        @Test
        void deniesWhenNoAssignmentCarriesThePermission() {
            roleAssignmentPort.add(actorUserId, activeAssignment(ScopeRef.global(), "OTHER_PERMISSION"));
            assertThat(service.hasPermission(actor, PERMISSION)).isFalse();
        }

        @Test
        void deniesWhenActorHasNoAssignmentsAtAll() {
            assertThat(service.hasPermission(actor, PERMISSION)).isFalse();
        }
    }

    @Nested
    class ScopeWalkUp {

        private final UUID facility1 = UUID.randomUUID();
        private final UUID facility2 = UUID.randomUUID();
        private final UUID departmentAtFacility1 = UUID.randomUUID();
        private final UUID departmentAtFacility2 = UUID.randomUUID();
        // Two different "Ward B"s, same intent, different facility, different id.
        private final UUID wardBAtFacility1 = UUID.randomUUID();
        private final UUID wardBAtFacility2 = UUID.randomUUID();

        @BeforeEach
        void wireHierarchy() {
            scopeHierarchyPort.setParent(
                    ScopeRef.of(ScopeType.WARD, wardBAtFacility1), ScopeRef.of(ScopeType.DEPARTMENT, departmentAtFacility1));
            scopeHierarchyPort.setParent(
                    ScopeRef.of(ScopeType.WARD, wardBAtFacility2), ScopeRef.of(ScopeType.DEPARTMENT, departmentAtFacility2));
            scopeHierarchyPort.setParent(
                    ScopeRef.of(ScopeType.DEPARTMENT, departmentAtFacility1), ScopeRef.of(ScopeType.FACILITY, facility1));
            scopeHierarchyPort.setParent(
                    ScopeRef.of(ScopeType.DEPARTMENT, departmentAtFacility2), ScopeRef.of(ScopeType.FACILITY, facility2));
        }

        @Test
        void wardScopedActorSeesTheirOwnWard() {
            roleAssignmentPort.add(actorUserId,
                    activeAssignment(ScopeRef.of(ScopeType.WARD, wardBAtFacility1), PERMISSION));

            assertThat(service.hasPermission(actor, PERMISSION, ScopeRef.of(ScopeType.WARD, wardBAtFacility1))).isTrue();
        }

        @Test
        void wardScopedActorCannotSeeSameNamedWardAtAnotherFacility() {
            roleAssignmentPort.add(actorUserId,
                    activeAssignment(ScopeRef.of(ScopeType.WARD, wardBAtFacility1), PERMISSION));

            assertThat(service.hasPermission(actor, PERMISSION, ScopeRef.of(ScopeType.WARD, wardBAtFacility2))).isFalse();
        }

        @Test
        void departmentScopedActorSeesWardsWithinTheirDepartmentViaWalkUp() {
            roleAssignmentPort.add(actorUserId,
                    activeAssignment(ScopeRef.of(ScopeType.DEPARTMENT, departmentAtFacility1), PERMISSION));

            assertThat(service.hasPermission(actor, PERMISSION, ScopeRef.of(ScopeType.WARD, wardBAtFacility1))).isTrue();
        }

        @Test
        void departmentScopedActorCannotSeeAnotherFacilitysWard() {
            roleAssignmentPort.add(actorUserId,
                    activeAssignment(ScopeRef.of(ScopeType.DEPARTMENT, departmentAtFacility1), PERMISSION));

            assertThat(service.hasPermission(actor, PERMISSION, ScopeRef.of(ScopeType.WARD, wardBAtFacility2))).isFalse();
        }

        @Test
        void globalScopedActorSeesEverything() {
            roleAssignmentPort.add(actorUserId, activeAssignment(ScopeRef.global(), PERMISSION));

            assertThat(service.hasPermission(actor, PERMISSION, ScopeRef.of(ScopeType.WARD, wardBAtFacility2))).isTrue();
        }
    }

    @Nested
    class ExpiryAndRevocation {

        @Test
        void excludesAssignmentsThatHaveExpired() {
            RoleAssignmentView expired = new RoleAssignmentView(
                    UUID.randomUUID(), "DOCTOR", ScopeRef.global(),
                    NOW.minusSeconds(7200), NOW.minusSeconds(3600), null, Set.of(PERMISSION));
            roleAssignmentPort.add(actorUserId, expired);

            assertThat(service.hasPermission(actor, PERMISSION)).isFalse();
        }

        @Test
        void excludesAssignmentsThatHaveBeenRevoked() {
            RoleAssignmentView revoked = new RoleAssignmentView(
                    UUID.randomUUID(), "DOCTOR", ScopeRef.global(),
                    NOW.minusSeconds(7200), null, NOW.minusSeconds(60), Set.of(PERMISSION));
            roleAssignmentPort.add(actorUserId, revoked);

            assertThat(service.hasPermission(actor, PERMISSION)).isFalse();
        }

        @Test
        void excludesAssignmentsNotYetValid() {
            RoleAssignmentView future = new RoleAssignmentView(
                    UUID.randomUUID(), "DOCTOR", ScopeRef.global(),
                    NOW.plusSeconds(3600), null, null, Set.of(PERMISSION));
            roleAssignmentPort.add(actorUserId, future);

            assertThat(service.hasPermission(actor, PERMISSION)).isFalse();
        }

        @Test
        void includesAssignmentsWithNoEndDate() {
            RoleAssignmentView openEnded = activeAssignment(ScopeRef.global(), PERMISSION);
            roleAssignmentPort.add(actorUserId, openEnded);

            assertThat(service.hasPermission(actor, PERMISSION)).isTrue();
        }
    }

    @Nested
    class PatientAccess {

        private final UUID patientId = UUID.randomUUID();

        @Test
        void deniedWhenActorLacksThePermission() {
            PolicyResult result = service.checkPatientAccess(actor, PERMISSION, patientId, ScopeRef.global());
            assertThat(result).isEqualTo(PolicyResult.DENIED);
        }

        @Test
        void allowedWhenActorHasPermissionAndACareRelationship() {
            roleAssignmentPort.add(actorUserId, activeAssignment(ScopeRef.global(), PERMISSION));
            careRelationshipPort.grant(actorUserId, patientId);

            PolicyResult result = service.checkPatientAccess(actor, PERMISSION, patientId, ScopeRef.global());
            assertThat(result).isEqualTo(PolicyResult.ALLOWED);
        }

        @Test
        void breakGlassRequiredWhenActorHasPermissionButNoCareRelationship() {
            roleAssignmentPort.add(actorUserId, activeAssignment(ScopeRef.global(), PERMISSION));

            PolicyResult result = service.checkPatientAccess(actor, PERMISSION, patientId, ScopeRef.global());
            assertThat(result).isEqualTo(PolicyResult.BREAK_GLASS_REQUIRED);
        }

        @Test
        void deniedTakesPriorityOverMissingCareRelationshipWhenScopeDoesNotMatch() {
            UUID otherWard = UUID.randomUUID();
            UUID actorWard = UUID.randomUUID();
            roleAssignmentPort.add(actorUserId, activeAssignment(ScopeRef.of(ScopeType.WARD, actorWard), PERMISSION));
            careRelationshipPort.grant(actorUserId, patientId);

            PolicyResult result = service.checkPatientAccess(
                    actor, PERMISSION, patientId, ScopeRef.of(ScopeType.WARD, otherWard));
            assertThat(result).isEqualTo(PolicyResult.DENIED);
        }
    }

    @Nested
    class BreakGlassRecording {

        private final UUID patientId = UUID.randomUUID();

        @Test
        void writesTheAuditRow() {
            service.recordBreakGlass(actor, patientId, "CLINICAL_NOTE", UUID.randomUUID(), "Unconscious patient, no attending on file");

            assertThat(auditRecorder.recorded).hasSize(1);
            RecordingAuditRecorder.Recorded entry = auditRecorder.recorded.get(0);
            assertThat(entry.actorUserId()).isEqualTo(actorUserId);
            assertThat(entry.patientId()).isEqualTo(patientId);
            assertThat(entry.justification()).isEqualTo("Unconscious patient, no attending on file");
        }

        @Test
        void rejectsAMissingJustification() {
            assertThatThrownBy(() -> service.recordBreakGlass(actor, patientId, "CLINICAL_NOTE", UUID.randomUUID(), " "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(auditRecorder.recorded).isEmpty();
        }
    }
}
