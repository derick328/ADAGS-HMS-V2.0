package com.adags.modules.inpatient;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// AdmissionRepository#findAwaitingPlacement — the claim worklist's source
// (17-ward-nurse.md §8). A patient the doctor marks ADMITTED has an admission
// row but no bed assignment until a nurse places them; such a patient is in
// no ward roster (findByCurrentWardId needs an active bed) and accrues no
// bed-days. This query is the one surface that makes them visible, so it must
// return exactly the admissions that are active and not yet bedded, scoped to
// the facility.
class AdmissionAwaitingPlacementTest extends IntegrationTestSupport {

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
    void returnsAnActiveAdmissionThatHasNoBedAssignment() {
        UUID admissionId = insertAdmission();

        List<Admission> awaiting = admissionRepository.findAwaitingPlacement(BOOTSTRAP_FACILITY_ID);

        assertThat(awaiting).extracting(Admission::id).containsExactly(admissionId);
    }

    @Test
    void excludesAnAdmissionOnceItHasAnActiveBedAssignment() {
        UUID admissionId = insertAdmission();
        assignBed(admissionId);

        assertThat(admissionRepository.findAwaitingPlacement(BOOTSTRAP_FACILITY_ID)).isEmpty();
    }

    @Test
    void includesAnAdmissionWhoseBedAssignmentHasBeenReleased() {
        UUID admissionId = insertAdmission();
        assignBed(admissionId);
        jdbcTemplate.update("UPDATE bed_assignment SET released_at = now() WHERE admission_id = :id",
                new MapSqlParameterSource("id", admissionId));

        assertThat(admissionRepository.findAwaitingPlacement(BOOTSTRAP_FACILITY_ID))
                .extracting(Admission::id).containsExactly(admissionId);
    }

    @Test
    void excludesAnAdmissionThatHasAlreadyDeparted() {
        UUID admissionId = insertAdmission();
        jdbcTemplate.update("UPDATE admission SET actual_departure_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", admissionId));

        assertThat(admissionRepository.findAwaitingPlacement(BOOTSTRAP_FACILITY_ID)).isEmpty();
    }

    @Test
    void isScopedToTheFacility() {
        insertAdmission();

        assertThat(admissionRepository.findAwaitingPlacement(UUID.randomUUID())).isEmpty();
    }

    private UUID insertAdmission() {
        UUID patientId = insertPatient();
        UUID encounterId = insertEncounter(patientId);
        return jdbcTemplate.queryForObject("""
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
    }

    private void assignBed(UUID admissionId) {
        UUID wardId = jdbcTemplate.queryForObject("""
                        INSERT INTO ward (facility_id, department_id, code, name, ward_class, daily_rate_minor, currency_code)
                        VALUES (:facilityId, :departmentId, :code, 'Test Ward', 'GENERAL', 10000, 'TZS')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", insertDepartment())
                        .addValue("code", "W-" + UUID.randomUUID().toString().substring(0, 6)),
                UUID.class);
        UUID bedId = jdbcTemplate.queryForObject("""
                        INSERT INTO bed (ward_id, label, bed_type, status)
                        VALUES (:wardId, 'Bed 1', 'STANDARD', 'AVAILABLE')
                        RETURNING id
                        """,
                new MapSqlParameterSource("wardId", wardId), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO bed_assignment (admission_id, bed_id, assigned_by)
                        VALUES (:admissionId, :bedId, (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource().addValue("admissionId", admissionId).addValue("bedId", bedId));
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
