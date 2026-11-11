package com.adags.modules.inpatient;

import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.BedAssignmentRepository;
import com.adags.modules.inpatient.domain.BedNotAvailableException;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.BedStatus;
import com.adags.modules.inpatient.domain.PlacementReason;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// root CLAUDE.md "Concurrency": UNIQUE (bed_id) WHERE released_at IS NULL —
// "That partial index is the whole protection against two nurses assigning
// the same bed. A read-then-write check loses the race." Fires real
// concurrent transactions at the same bed and proves the database rejects
// all but one, independent of any application-level pre-check.
class BedAssignmentConcurrencyTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private BedAssignmentRepository bedAssignmentRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE bed_assignment, bed, admission, ward, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void concurrentAssignmentsToTheSameBedLeaveExactlyOneActive() throws Exception {
        UUID wardId = insertFixtureWard();
        UUID bedId = bedRepository.insert(new Bed(null, wardId, "Bed 1", "STANDARD", BedStatus.AVAILABLE, null)).id();
        UUID admittingUser = bootstrapAdminId();

        int racers = 5;
        List<UUID> admissionIds = IntStream.range(0, racers)
                .mapToObj(i -> insertFixtureAdmission())
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<BedAssignment>> futures = admissionIds.stream()
                    .map(admissionId -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        try {
                            return bedAssignmentRepository.insert(BedAssignment.create(
                                    admissionId, bedId, WardClass.GENERAL, WardClass.GENERAL, null, admittingUser));
                        } catch (BedNotAvailableException ex) {
                            return null;
                        }
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            long successes = futures.stream().map(this::resolve).filter(r -> r != null).count();
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdown();
        }

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bed_assignment WHERE bed_id = :bedId AND released_at IS NULL",
                new MapSqlParameterSource("bedId", bedId), Integer.class);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void checkConstraintRejectsPlacedAboveOrderedClassWithNoReason() {
        UUID wardId = insertFixtureWard();
        UUID bedId = bedRepository.insert(new Bed(null, wardId, "Bed 1", "STANDARD", BedStatus.AVAILABLE, null)).id();
        UUID admissionId = insertFixtureAdmission();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO bed_assignment (admission_id, bed_id, assigned_by, placed_above_ordered_class, placement_reason)
                        VALUES (:admissionId, :bedId, (SELECT id FROM app_user WHERE username = 'admin'), true, NULL)
                        """,
                new MapSqlParameterSource().addValue("admissionId", admissionId).addValue("bedId", bedId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void releasingTheActiveAssignmentFreesTheBedForANewOne() {
        UUID wardId = insertFixtureWard();
        UUID bedId = bedRepository.insert(new Bed(null, wardId, "Bed 1", "STANDARD", BedStatus.AVAILABLE, null)).id();
        UUID admittingUser = bootstrapAdminId();
        UUID firstAdmission = insertFixtureAdmission();

        BedAssignment first = bedAssignmentRepository.insert(
                BedAssignment.create(firstAdmission, bedId, WardClass.GENERAL, WardClass.GENERAL, null, admittingUser));
        bedAssignmentRepository.release(first.id(), java.time.Instant.now());

        UUID secondAdmission = insertFixtureAdmission();
        BedAssignment second = bedAssignmentRepository.insert(
                BedAssignment.create(secondAdmission, bedId, WardClass.GENERAL, WardClass.GENERAL, null, admittingUser));

        assertThat(second.id()).isNotNull();
        assertThat(bedAssignmentRepository.findActiveByAdmissionId(secondAdmission).orElseThrow().bedId()).isEqualTo(bedId);
    }

    private Object resolve(Future<BedAssignment> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID insertFixtureWard() {
        UUID departmentId = insertFixtureDepartment();
        Ward ward = new Ward(null, BOOTSTRAP_FACILITY_ID, departmentId, "W-" + UUID.randomUUID().toString().substring(0, 6),
                "Test Ward", WardClass.GENERAL, null, new Money(10000, "TZS"), true, null);
        return wardRepository.insert(ward).id();
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
