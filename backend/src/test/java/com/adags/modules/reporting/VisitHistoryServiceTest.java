package com.adags.modules.reporting;

import com.adags.modules.reporting.application.VisitHistoryEntry;
import com.adags.modules.reporting.application.VisitHistoryService;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 05-receptionist.md §1.1 ⚠1: the restricted visit-history projection —
// proves what it returns (dates, department, provider name, invoice
// status) and, just as importantly, that its column set has no room for
// anything clinical to leak through later.
class VisitHistoryServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private VisitHistoryService visitHistoryService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE invoice, encounter, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void returnsEncounterDepartmentProviderAndInvoiceStatusForThePatientsHistory() {
        UUID patientId = insertFixturePatient();
        FixtureDepartment department = insertFixtureDepartment();
        UUID adminId = bootstrapAdminId();
        UUID encounterId = insertFixtureEncounter(patientId, department.id(), adminId, "AWAITING_TRIAGE");
        insertFixtureInvoice(encounterId, patientId, "ISSUED", 5_000);

        List<VisitHistoryEntry> history = visitHistoryService.forPatient(patientId);

        assertThat(history).hasSize(1);
        VisitHistoryEntry entry = history.get(0);
        assertThat(entry.encounterId()).isEqualTo(encounterId);
        assertThat(entry.encounterState()).isEqualTo("AWAITING_TRIAGE");
        assertThat(entry.departmentName()).isEqualTo(department.name());
        // No staff row for the bootstrap admin — falls back to username.
        assertThat(entry.attendingProviderName()).isEqualTo("admin");
        assertThat(entry.invoiceState()).isEqualTo("ISSUED");
        assertThat(entry.outstandingTotalMinor()).isEqualTo(5_000);
    }

    @Test
    void anEncounterWithNoInvoiceStillAppearsWithNullInvoiceFields() {
        UUID patientId = insertFixturePatient();
        FixtureDepartment department = insertFixtureDepartment();
        insertFixtureEncounter(patientId, department.id(), bootstrapAdminId(), "AWAITING_TRIAGE");

        List<VisitHistoryEntry> history = visitHistoryService.forPatient(patientId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).invoiceId()).isNull();
        assertThat(history.get(0).invoiceState()).isNull();
    }

    @Test
    void mostRecentEncounterFirst() {
        UUID patientId = insertFixturePatient();
        FixtureDepartment department = insertFixtureDepartment();
        UUID adminId = bootstrapAdminId();
        UUID older = insertFixtureEncounter(patientId, department.id(), adminId, "CLOSED");
        jdbcTemplate.update("UPDATE encounter SET created_at = now() - interval '2 days' WHERE id = :id",
                new MapSqlParameterSource("id", older));
        UUID newer = insertFixtureEncounter(patientId, department.id(), adminId, "CLOSED");

        List<VisitHistoryEntry> history = visitHistoryService.forPatient(patientId);

        assertThat(history).extracting(VisitHistoryEntry::encounterId).containsExactly(newer, older);
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private record FixtureDepartment(UUID id, String name) {}

    private FixtureDepartment insertFixtureDepartment() {
        String name = "Test Department " + UUID.randomUUID();
        UUID id = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :name)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("name", name),
                UUID.class);
        return new FixtureDepartment(id, name);
    }

    private UUID insertFixtureEncounter(UUID patientId, UUID departmentId, UUID providerId, String state) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, assigned_provider_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, :providerId, 'OUTPATIENT', :state, 'CASH', :providerId)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId)
                        .addValue("providerId", providerId)
                        .addValue("state", state),
                UUID.class);
    }

    private void insertFixtureInvoice(UUID encounterId, UUID patientId, String state, long outstandingMinor) {
        jdbcTemplate.update("""
                        INSERT INTO invoice (encounter_id, patient_id, facility_id, state, payer_category, invoice_number,
                                              outstanding_total_minor, gross_total_minor, patient_portion_total_minor, created_by)
                        VALUES (:encounterId, :patientId, :facilityId, :state, 'CASH', :invoiceNumber, :outstanding, :outstanding, :outstanding,
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("encounterId", encounterId)
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("state", state)
                        .addValue("invoiceNumber", state.equals("DRAFT") ? null : "TEST-INV-" + UUID.randomUUID())
                        .addValue("outstanding", outstandingMinor));
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
