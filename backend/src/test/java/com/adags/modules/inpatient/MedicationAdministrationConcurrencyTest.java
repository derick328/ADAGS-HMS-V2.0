package com.adags.modules.inpatient;

import com.adags.modules.inpatient.domain.MedicationAdministration;
import com.adags.modules.inpatient.domain.MedicationAdministrationRepository;
import com.adags.modules.inpatient.domain.MedicationAdministrationStatus;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 17-ward-nurse.md §5: the MAR's real guards, proved against Postgres, not
// application code — root CLAUDE.md "Concurrency": "a double-tap must not
// chart two doses," and §5.3's two-person CHECK.
class MedicationAdministrationConcurrencyTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MedicationAdministrationRepository medicationAdministrationRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE medication_administration, admission, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void checkConstraintRejectsAWitnessEqualToTheAdministeringNurse() {
        UUID admissionId = insertFixtureAdmission();
        UUID prescriptionLineId = UUID.randomUUID();
        UUID nurseId = bootstrapAdminId();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO medication_administration
                            (admission_id, prescription_line_id, scheduled_at, status, administered_at,
                             administered_by, witness_user_id)
                        VALUES (:admissionId, :prescriptionLineId, now(), 'GIVEN', now(), :nurseId, :nurseId)
                        """,
                new MapSqlParameterSource()
                        .addValue("admissionId", admissionId)
                        .addValue("prescriptionLineId", prescriptionLineId)
                        .addValue("nurseId", nurseId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void generatingTheScheduleTwiceForTheSameLineDoesNotDuplicateSlots() {
        UUID admissionId = insertFixtureAdmission();
        UUID prescriptionLineId = UUID.randomUUID();
        Instant scheduledAt = Instant.now();

        List<MedicationAdministration> slots = List.of(
                MedicationAdministration.dueSlot(admissionId, prescriptionLineId, scheduledAt));
        medicationAdministrationRepository.insertSchedule(slots);
        medicationAdministrationRepository.insertSchedule(slots);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM medication_administration WHERE prescription_line_id = :lineId",
                new MapSqlParameterSource("lineId", prescriptionLineId), Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void concurrentMarksOfTheSameSlotLeaveExactlyOneSuccess() throws Exception {
        UUID admissionId = insertFixtureAdmission();
        UUID prescriptionLineId = UUID.randomUUID();
        UUID nurseId = bootstrapAdminId();
        Instant scheduledAt = Instant.now();

        medicationAdministrationRepository.insertSchedule(
                List.of(MedicationAdministration.dueSlot(admissionId, prescriptionLineId, scheduledAt)));
        UUID slotId = jdbcTemplate.queryForObject(
                "SELECT id FROM medication_administration WHERE prescription_line_id = :lineId",
                new MapSqlParameterSource("lineId", prescriptionLineId), UUID.class);

        int racers = 5;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<Optional<MedicationAdministration>>> futures = IntStream.range(0, racers)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        MedicationAdministration marked = new MedicationAdministration(
                                slotId, admissionId, prescriptionLineId, scheduledAt, MedicationAdministrationStatus.GIVEN,
                                Instant.now(), nurseId, "1g", null, null, null, null, null, null);
                        return medicationAdministrationRepository.mark(marked);
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            long successes = futures.stream().map(this::resolve).filter(Optional::isPresent).count();
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    private Optional<MedicationAdministration> resolve(Future<Optional<MedicationAdministration>> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID insertFixtureAdmission() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

        UUID departmentId = insertFixtureDepartment();

        UUID encounterId = jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'ADMITTED', 'CASH',
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

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
