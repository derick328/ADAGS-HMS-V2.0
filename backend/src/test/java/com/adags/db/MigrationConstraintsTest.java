package com.adags.db;

import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Proves the constraints that make V1/V2 more than documentation: a trigger
// that rejects ADMIN holding a clinical permission, and a CHECK that rejects
// an elevation_request reviewed by its own requester. Each statement runs
// autocommitted (no @Transactional here) so a rejected statement's aborted
// transaction never poisons the next assertion in the same test.
class MigrationConstraintsTest extends IntegrationTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void grantingAdminAClinicalPermissionIsRejected() {
        UUID adminRoleId = queryUuid("SELECT id FROM role WHERE code = 'ADMIN'");
        UUID clinicalPermissionId = queryUuid("SELECT id FROM permission WHERE code = 'CLINICAL_NOTE_READ'");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO role_permission (role_id, permission_id) VALUES (:roleId, :permissionId)",
                new MapSqlParameterSource().addValue("roleId", adminRoleId).addValue("permissionId", clinicalPermissionId)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ADMIN cannot hold clinical permission");
    }

    @Test
    void grantingAdminANonClinicalPermissionIsAccepted() {
        UUID adminRoleId = queryUuid("SELECT id FROM role WHERE code = 'ADMIN'");
        String code = "TEST_NON_CLINICAL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID permissionId = jdbcTemplate.queryForObject(
                "INSERT INTO permission (code, description, is_clinical) VALUES (:code, 'test fixture', FALSE) RETURNING id",
                new MapSqlParameterSource("code", code), UUID.class);

        jdbcTemplate.update(
                "INSERT INTO role_permission (role_id, permission_id) VALUES (:roleId, :permissionId)",
                new MapSqlParameterSource().addValue("roleId", adminRoleId).addValue("permissionId", permissionId));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM role_permission WHERE role_id = :roleId AND permission_id = :permissionId",
                new MapSqlParameterSource().addValue("roleId", adminRoleId).addValue("permissionId", permissionId),
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void elevationRequestRejectsReviewerEqualToRequester() {
        UUID userId = insertFixtureUser();
        UUID doctorRoleId = queryUuid("SELECT id FROM role WHERE code = 'DOCTOR'");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO elevation_request (requested_by, role_id, justification, reviewed_by, status)
                        VALUES (:userId, :roleId, 'self-review attempt', :userId, 'APPROVED')
                        """,
                new MapSqlParameterSource().addValue("userId", userId).addValue("roleId", doctorRoleId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void elevationRequestAllowsADistinctReviewer() {
        UUID requester = insertFixtureUser();
        UUID reviewer = insertFixtureUser();
        UUID doctorRoleId = queryUuid("SELECT id FROM role WHERE code = 'DOCTOR'");

        jdbcTemplate.update("""
                        INSERT INTO elevation_request (requested_by, role_id, justification, reviewed_by, status)
                        VALUES (:requester, :roleId, 'locum cover', :reviewer, 'APPROVED')
                        """,
                new MapSqlParameterSource()
                        .addValue("requester", requester)
                        .addValue("reviewer", reviewer)
                        .addValue("roleId", doctorRoleId));
    }

    @Test
    void auditLogRejectsAFacilityIdThatDoesNotExist() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO audit_log (action, resource_type, facility_id) VALUES ('TEST', 'TEST', :facilityId)",
                new MapSqlParameterSource("facilityId", UUID.randomUUID())))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void auditLogFallsBackToTheBootstrapFacilityWhenNoneIsGiven() {
        jdbcTemplate.update(
                "INSERT INTO audit_log (action, resource_type) VALUES ('TEST', 'TEST')",
                new MapSqlParameterSource());

        UUID facilityId = jdbcTemplate.queryForObject(
                "SELECT facility_id FROM audit_log WHERE action = 'TEST' ORDER BY occurred_at DESC LIMIT 1",
                new MapSqlParameterSource(), UUID.class);
        assertThat(facilityId).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void elevationRequestRejectsGlobalScopeWithANonNullScopeId() {
        UUID userId = insertFixtureUser();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO elevation_request (requested_by, permission_id, justification, scope_type, scope_id)
                        VALUES (:userId, (SELECT id FROM permission LIMIT 1), 'bad global scope', 'GLOBAL', gen_random_uuid())
                        """,
                new MapSqlParameterSource("userId", userId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void elevationRequestRejectsANonGlobalScopeWithANullScopeId() {
        UUID userId = insertFixtureUser();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO elevation_request (requested_by, permission_id, justification, scope_type, scope_id)
                        VALUES (:userId, (SELECT id FROM permission LIMIT 1), 'missing scope id', 'DEPARTMENT', NULL)
                        """,
                new MapSqlParameterSource("userId", userId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void partitionMaintenanceFunctionIsIdempotentAndCreatesAFuturePartition() {
        jdbcTemplate.update(
                "SELECT fn_ensure_audit_log_partition(DATE '2030-01-01')",
                new MapSqlParameterSource());
        // Second call for the same month must not fail with "relation already exists".
        jdbcTemplate.update(
                "SELECT fn_ensure_audit_log_partition(DATE '2030-01-01')",
                new MapSqlParameterSource());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_class WHERE relname = 'audit_log_2030_01'",
                new MapSqlParameterSource(), Integer.class);
        assertThat(count).isEqualTo(1);
    }

    private UUID insertFixtureUser() {
        String username = "fixture_" + UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", username), UUID.class);
    }

    private UUID queryUuid(String sql) {
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), UUID.class);
    }
}
