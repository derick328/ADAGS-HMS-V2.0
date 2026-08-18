package com.adags.modules.inpatient;

import com.adags.modules.inpatient.domain.NursingAssignment;
import com.adags.modules.inpatient.domain.NursingAssignmentRepository;
import com.adags.modules.inpatient.domain.Shift;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 17-ward-nurse.md §8.2: care responsibility as a roster, not a single
// assignedNurse field — proves the round trip and the per-(admission,
// nurse, shift, date) uniqueness that lets the same slot be queried back
// without ambiguity.
class NursingAssignmentRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private NursingAssignmentRepository nursingAssignmentRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE nursing_assignment, admission, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void insertAndFindForShiftRoundTrips() {
        UUID admissionId = insertFixtureAdmission();
        UUID nurseId = bootstrapAdminId();
        LocalDate today = LocalDate.now();

        NursingAssignment saved = nursingAssignmentRepository.insert(
                new NursingAssignment(null, admissionId, nurseId, Shift.MORNING, today, nurseId, null));

        assertThat(saved.id()).isNotNull();
        assertThat(nursingAssignmentRepository.findForShift(admissionId, today, Shift.MORNING)).contains(saved);
        assertThat(nursingAssignmentRepository.existsForNurseAndAdmission(admissionId, nurseId)).isTrue();
    }

    @Test
    void existsForNurseAndAdmissionIsFalseForAnUnrelatedNurse() {
        UUID admissionId = insertFixtureAdmission();
        UUID nurseId = bootstrapAdminId();
        nursingAssignmentRepository.insert(
                new NursingAssignment(null, admissionId, nurseId, Shift.NIGHT, LocalDate.now(), nurseId, null));

        assertThat(nursingAssignmentRepository.existsForNurseAndAdmission(admissionId, UUID.randomUUID())).isFalse();
    }

    @Test
    void findByAdmissionIdReturnsEveryShiftAssigned() {
        UUID admissionId = insertFixtureAdmission();
        UUID nurseId = bootstrapAdminId();
        LocalDate today = LocalDate.now();
        nursingAssignmentRepository.insert(new NursingAssignment(null, admissionId, nurseId, Shift.MORNING, today, nurseId, null));
        nursingAssignmentRepository.insert(new NursingAssignment(null, admissionId, nurseId, Shift.NIGHT, today, nurseId, null));

        assertThat(nursingAssignmentRepository.findByAdmissionId(admissionId)).hasSize(2);
    }

    @Test
    void uniqueConstraintRejectsADuplicateSlot() {
        UUID admissionId = insertFixtureAdmission();
        UUID nurseId = bootstrapAdminId();
        LocalDate today = LocalDate.now();
        nursingAssignmentRepository.insert(new NursingAssignment(null, admissionId, nurseId, Shift.MORNING, today, nurseId, null));

        assertThatThrownBy(() -> nursingAssignmentRepository.insert(
                new NursingAssignment(null, admissionId, nurseId, Shift.MORNING, today, nurseId, null)))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertFixtureAdmission() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);

        UUID encounterId = jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'IN_WARD', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId),
                UUID.class);

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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
