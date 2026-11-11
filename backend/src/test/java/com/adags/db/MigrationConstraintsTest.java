package com.adags.db;

import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
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

    // IntegrationTestSupport's Postgres container is a suite-wide singleton
    // — a fixture patient/department/encounter left behind here would
    // otherwise be visible to a later test's duplicate-detection or
    // one-open-encounter-per-patient assertions in a different class.
    @AfterEach
    void cleanUpFixtures() {
        jdbcTemplate.update("TRUNCATE encounter, patient CASCADE", new MapSqlParameterSource());
    }

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
                "INSERT INTO permission (code, module, description, is_clinical) VALUES (:code, 'admin', 'test fixture', FALSE) RETURNING id",
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
        jdbcTemplate.queryForObject(
                "SELECT fn_ensure_audit_log_partition(DATE '2030-01-01')",
                new MapSqlParameterSource(), (rs, rowNum) -> null);
        // Second call for the same month must not fail with "relation already exists".
        jdbcTemplate.queryForObject(
                "SELECT fn_ensure_audit_log_partition(DATE '2030-01-01')",
                new MapSqlParameterSource(), (rs, rowNum) -> null);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_class WHERE relname = 'audit_log_2030_01'",
                new MapSqlParameterSource(), Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void patientNationalIdRejectsADuplicateNonNullValue() {
        String nationalId = "NID-" + UUID.randomUUID();
        insertFixturePatient("Amina", "Hassan", nationalId);

        assertThatThrownBy(() -> insertFixturePatient("Different", "Person", nationalId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void patientNationalIdAllowsManyNullValues() {
        insertFixturePatient("First", "Patient", null);
        // Must not collide on NULL the way a plain UNIQUE index would on some configurations.
        insertFixturePatient("Second", "Patient", null);
    }

    @Test
    void patientMergeChainIntoAnAlreadyMergedRecordIsRejected() {
        UUID original = insertFixturePatient("Original", "Record", null);
        UUID survivor = insertFixturePatient("Survivor", "Record", null);
        UUID thirdRecord = insertFixturePatient("Third", "Record", null);

        jdbcTemplate.update("UPDATE patient SET merged_into_id = :survivor WHERE id = :original",
                new MapSqlParameterSource().addValue("survivor", survivor).addValue("original", original));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE patient SET merged_into_id = :original WHERE id = :thirdRecord",
                new MapSqlParameterSource().addValue("original", original).addValue("thirdRecord", thirdRecord)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("already merged");
    }

    @Test
    void patientMergeIsReversible() {
        UUID original = insertFixturePatient("Original", "Record", null);
        UUID survivor = insertFixturePatient("Survivor", "Record", null);

        jdbcTemplate.update("UPDATE patient SET merged_into_id = :survivor WHERE id = :original",
                new MapSqlParameterSource().addValue("survivor", survivor).addValue("original", original));

        // Un-merge — 01-architecture.md §5.2 requires merge to be reversible.
        jdbcTemplate.update("UPDATE patient SET merged_into_id = NULL WHERE id = :original",
                new MapSqlParameterSource("original", original));

        UUID mergedInto = jdbcTemplate.queryForObject(
                "SELECT merged_into_id FROM patient WHERE id = :original",
                new MapSqlParameterSource("original", original), UUID.class);
        assertThat(mergedInto).isNull();
    }

    // 05-receptionist.md ⚠7 / root CLAUDE.md "Concurrency": the raw
    // constraint, independent of OpenEncounterServiceTest's application-
    // level recovery test — proves the database itself rejects a second
    // open encounter even if some future caller bypasses the service.
    @Test
    void encounterRejectsASecondOpenEncounterForTheSamePatient() {
        UUID patientId = insertFixturePatient("Amina", "Hassan", null);
        UUID departmentId = insertFixtureDepartment();
        insertFixtureEncounter(patientId, departmentId, "AWAITING_TRIAGE");

        assertThatThrownBy(() -> insertFixtureEncounter(patientId, departmentId, "AWAITING_PAYMENT"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void encounterAllowsANewOpenEncounterOnceThePriorOneIsTerminal() {
        UUID patientId = insertFixturePatient("Amina", "Hassan", null);
        UUID departmentId = insertFixtureDepartment();
        insertFixtureEncounter(patientId, departmentId, "CLOSED");

        // Must not throw — a terminal encounter does not hold the slot.
        insertFixtureEncounter(patientId, departmentId, "AWAITING_TRIAGE");
    }

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES ('00000000-0000-0000-0000-000000000001', (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private void insertFixtureEncounter(UUID patientId, UUID departmentId, String state) {
        jdbcTemplate.update("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, '00000000-0000-0000-0000-000000000001', :departmentId,
                                'OUTPATIENT', :state, 'CASH', (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("departmentId", departmentId)
                        .addValue("state", state));
    }

    private UUID insertFixturePatient(String givenName, String familyName, String nationalId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone, national_id)
                        VALUES (:hospitalNo, :givenName, :familyName, DATE '1990-01-01', 'FEMALE', '+255700000000', :nationalId)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("hospitalNo", "TEST-" + UUID.randomUUID())
                        .addValue("givenName", givenName)
                        .addValue("familyName", familyName)
                        .addValue("nationalId", nationalId, java.sql.Types.VARCHAR),
                UUID.class);
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
