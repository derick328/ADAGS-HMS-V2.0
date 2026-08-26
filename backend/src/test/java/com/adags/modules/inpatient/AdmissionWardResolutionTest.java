package com.adags.modules.inpatient;

import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.shared.kernel.WardSummary;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// AdmissionRepository#findCurrentWardByEncounterId — the read path pharmacy's
// Dispensing Queue uses (via InpatientFacade#findActiveAdmissionWard) to group
// its inpatient rows by ward. The ward is the encounter's active admission's
// unreleased BedAssignment's bed's ward; it is absent the moment any link in
// that chain is missing.
class AdmissionWardResolutionTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private AdmissionRepository admissionRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE bed_assignment, bed, admission, ward, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void resolvesTheWardForAnEncounterWithAnActiveAdmissionInAnActiveBed() {
        Fixture f = admittedInBed("Medical Ward B");

        Optional<WardSummary> ward = admissionRepository.findCurrentWardByEncounterId(f.encounterId());

        assertThat(ward).contains(new WardSummary(f.wardId(), "Medical Ward B"));
    }

    @Test
    void isEmptyForAnOutpatientEncounterWithNoAdmission() {
        UUID encounterId = insertEncounter(insertPatient());

        assertThat(admissionRepository.findCurrentWardByEncounterId(encounterId)).isEmpty();
    }

    @Test
    void isEmptyOnceTheAdmissionHasAnActualDeparture() {
        Fixture f = admittedInBed("Medical Ward B");
        jdbcTemplate.update("UPDATE admission SET actual_departure_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", f.admissionId()));

        assertThat(admissionRepository.findCurrentWardByEncounterId(f.encounterId())).isEmpty();
    }

    @Test
    void isEmptyWhenTheBedAssignmentHasBeenReleased() {
        Fixture f = admittedInBed("Medical Ward B");
        jdbcTemplate.update("UPDATE bed_assignment SET released_at = now() WHERE admission_id = :id",
                new MapSqlParameterSource("id", f.admissionId()));

        assertThat(admissionRepository.findCurrentWardByEncounterId(f.encounterId())).isEmpty();
    }

    @Test
    void isEmptyForAnAdmissionThatHasNotYetBeenGivenABed() {
        Fixture f = admittedInBed("Medical Ward B");
        jdbcTemplate.update("DELETE FROM bed_assignment WHERE admission_id = :id",
                new MapSqlParameterSource("id", f.admissionId()));

        assertThat(admissionRepository.findCurrentWardByEncounterId(f.encounterId())).isEmpty();
    }

    private record Fixture(UUID encounterId, UUID admissionId, UUID bedId, UUID wardId) {
    }

    private Fixture admittedInBed(String wardName) {
        UUID patientId = insertPatient();
        UUID encounterId = insertEncounter(patientId);
        UUID wardId = insertWard(wardName);
        UUID bedId = insertBed(wardId);

        UUID admissionId = jdbcTemplate.queryForObject("""
                        INSERT INTO admission (encounter_id, patient_id, facility_id, admitting_doctor_id,
                                                ordered_ward_class, admitted_at)
                        VALUES (:encounterId, :patientId, :facilityId, (SELECT id FROM app_user WHERE username = 'admin'),
                                'GENERAL', now())
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("encounterId", encounterId)
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID),
                UUID.class);

        jdbcTemplate.update("""
                        INSERT INTO bed_assignment (admission_id, bed_id, assigned_by)
                        VALUES (:admissionId, :bedId, (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("admissionId", admissionId)
                        .addValue("bedId", bedId));

        return new Fixture(encounterId, admissionId, bedId, wardId);
    }

    private UUID insertPatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID insertEncounter(UUID patientId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'IN_WARD', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", insertDepartment()),
                UUID.class);
    }

    private UUID insertWard(String name) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO ward (facility_id, department_id, code, name, ward_class, daily_rate_minor, currency_code)
                        VALUES (:facilityId, :departmentId, :code, :name, 'GENERAL', 10000, 'TZS')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", insertDepartment())
                        .addValue("code", "W-" + UUID.randomUUID().toString().substring(0, 6))
                        .addValue("name", name),
                UUID.class);
    }

    private UUID insertBed(UUID wardId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO bed (ward_id, label, bed_type, status)
                        VALUES (:wardId, 'Bed 1', 'STANDARD', 'AVAILABLE')
                        RETURNING id
                        """,
                new MapSqlParameterSource("wardId", wardId), UUID.class);
    }

    private UUID insertDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }
}
