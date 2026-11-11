package com.adags.modules.inpatient;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// AdmissionRepository#findActiveByAdmittingDoctor — the doctor-side "Admitted"
// worklist (07-doctor.md §7/§11): the active admissions a given doctor
// ordered, bedded or not, excluding anyone already departed.
class AdmissionDoctorWorklistTest extends IntegrationTestSupport {

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
    void returnsAnActiveAdmissionOrderedByThisDoctorEvenBeforeItHasABed() {
        UUID doctorId = adminUserId();
        UUID admissionId = insertAdmission(doctorId);

        assertThat(admissionRepository.findActiveByAdmittingDoctor(doctorId))
                .extracting(Admission::id).containsExactly(admissionId);
    }

    @Test
    void excludesAdmissionsOrderedByAnotherDoctor() {
        insertAdmission(adminUserId());

        assertThat(admissionRepository.findActiveByAdmittingDoctor(UUID.randomUUID())).isEmpty();
    }

    @Test
    void excludesAnAdmissionThatHasAlreadyDeparted() {
        UUID doctorId = adminUserId();
        UUID admissionId = insertAdmission(doctorId);
        jdbcTemplate.update("UPDATE admission SET actual_departure_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", admissionId));

        assertThat(admissionRepository.findActiveByAdmittingDoctor(doctorId)).isEmpty();
    }

    private UUID adminUserId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }

    private UUID insertAdmission(UUID admittingDoctorId) {
        UUID patientId = insertPatient();
        UUID encounterId = insertEncounter(patientId);
        return jdbcTemplate.queryForObject("""
                        INSERT INTO admission (encounter_id, patient_id, facility_id, admitting_doctor_id,
                                                ordered_ward_class, admitted_at)
                        VALUES (:encounterId, :patientId, :facilityId, :admittingDoctorId, 'GENERAL', now())
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("encounterId", encounterId)
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("admittingDoctorId", admittingDoctorId),
                UUID.class);
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

    private UUID insertDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }
}
