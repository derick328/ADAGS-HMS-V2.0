package com.adags.modules.encounters;

import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.EncounterType;
import com.adags.modules.scheduling.application.CreateFollowUpAppointmentCommand;
import com.adags.modules.scheduling.application.CreateFollowUpAppointmentService;
import com.adags.modules.scheduling.domain.Appointment;
import com.adags.modules.scheduling.domain.AppointmentRepository;
import com.adags.modules.scheduling.domain.AppointmentStatus;
import com.adags.shared.kernel.PayerType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// 05-receptionist.md §5.2 "Send to triage".
class OpenEncounterServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private OpenEncounterService openEncounterService;

    @Autowired
    private EncounterRepository encounterRepository;

    @Autowired
    private CreateFollowUpAppointmentService createFollowUpAppointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE appointment, encounter, patient CASCADE", new MapSqlParameterSource());
    }

    // 05a-receptionist-decisions.md §3.3: arriving against a booked
    // follow-up marks it ATTENDED and records the new encounter, in the
    // same transaction as opening it (OpenEncounterTransaction).
    @Test
    void checkInAgainstABookedFollowUpMarksItAttended() {
        UUID patientId = insertFixturePatient();
        UUID departmentId = insertFixtureDepartment();
        UUID actor = bootstrapAdminId();
        UUID sourceEncounterId = openEncounterService.checkIn(
                new OpenEncounterCommand(patientId, departmentId, null, actor)).id();
        // Close the first encounter out of the way so the follow-up
        // check-in below doesn't just return it via the one-open-encounter
        // guard — the two-open-encounters-for-one-patient scenario proper
        // is a doctor/closure concern this test isn't exercising.
        encounterRepository.updateState(sourceEncounterId, EncounterState.CLOSED);
        Appointment appointment = createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                patientId, sourceEncounterId, java.time.Instant.now().plusSeconds(3600), null, actor));

        Encounter followUpEncounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patientId, departmentId, appointment.id(), actor));

        assertThat(followUpEncounter.appointmentId()).isEqualTo(appointment.id());
        Appointment reloaded = appointmentRepository.findById(appointment.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(AppointmentStatus.ATTENDED);
        assertThat(reloaded.attendedEncounterId()).isEqualTo(followUpEncounter.id());
    }

    @Test
    void walkInOpensAtFacilityWithoutChoosingADepartment() {
        UUID patientId = insertFixturePatient();
        Encounter encounter = openEncounterService.checkIn(new OpenEncounterCommand(
                patientId, null, null, bootstrapAdminId(), BOOTSTRAP_FACILITY_ID));

        assertThat(encounter.facilityId()).isEqualTo(BOOTSTRAP_FACILITY_ID);
        assertThat(encounter.departmentId()).isNull();
        assertThat(encounter.state()).isEqualTo(EncounterState.AWAITING_TRIAGE);
        assertThat(encounterRepository.findById(encounter.id()).orElseThrow().departmentId()).isNull();
    }

    @Test
    void checkInOpensAnAwaitingTriageEncounterAndWritesTheCreationEvent() {
        UUID patientId = insertFixturePatient();
        UUID departmentId = insertFixtureDepartment();
        UUID actor = bootstrapAdminId();

        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patientId, departmentId, null, actor));

        assertThat(encounter.state()).isEqualTo(EncounterState.AWAITING_TRIAGE);
        assertThat(encounter.type()).isEqualTo(EncounterType.OUTPATIENT);
        assertThat(encounter.payerType()).isEqualTo(PayerType.CASH);
        assertThat(encounter.facilityId()).isEqualTo(BOOTSTRAP_FACILITY_ID);
        assertThat(encounter.appointmentId()).isNull();

        Integer eventCount = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM encounter_event
                        WHERE encounter_id = :id AND from_state IS NULL AND to_state = 'AWAITING_TRIAGE'
                        """,
                new MapSqlParameterSource("id", encounter.id()), Integer.class);
        assertThat(eventCount).isEqualTo(1);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'EncounterOpened' AND payload->>'encounterId' = :id",
                new MapSqlParameterSource("id", encounter.id().toString()), Integer.class);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void secondCheckInForTheSamePatientReturnsTheExistingEncounterWithoutANewRowOrEvent() {
        UUID patientId = insertFixturePatient();
        UUID departmentId = insertFixtureDepartment();
        UUID actor = bootstrapAdminId();

        Encounter first = openEncounterService.checkIn(new OpenEncounterCommand(patientId, departmentId, null, actor));
        Encounter second = openEncounterService.checkIn(new OpenEncounterCommand(patientId, departmentId, null, actor));

        assertThat(second.id()).isEqualTo(first.id());

        Integer encounterCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM encounter WHERE patient_id = :patientId",
                new MapSqlParameterSource("patientId", patientId), Integer.class);
        assertThat(encounterCount).isEqualTo(1);

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM encounter_event WHERE encounter_id = :id",
                new MapSqlParameterSource("id", first.id()), Integer.class);
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    void returningPatientWithCompletedConsultationGetsANewTriageVisit() {
        UUID patientId = insertFixturePatient();
        UUID departmentId = insertFixtureDepartment();
        UUID actor = bootstrapAdminId();
        Encounter previous = openEncounterService.checkIn(
                new OpenEncounterCommand(patientId, departmentId, null, actor));
        encounterRepository.updateState(previous.id(), EncounterState.CONSULT_COMPLETE);

        OpenEncounterService.CheckInResult result = openEncounterService.checkInResult(
                new OpenEncounterCommand(patientId, departmentId, null, actor));

        assertThat(result.existingEncounter()).isFalse();
        assertThat(result.encounter().id()).isNotEqualTo(previous.id());
        assertThat(result.encounter().state()).isEqualTo(EncounterState.AWAITING_TRIAGE);
        assertThat(encounterRepository.findById(previous.id()).orElseThrow().state()).isEqualTo(EncounterState.CLOSED);
        assertThat(encounterRepository.findOpenByPatientId(patientId).orElseThrow().id()).isEqualTo(result.encounter().id());
        assertThat(openEncounterService.checkInResult(
                new OpenEncounterCommand(patientId, departmentId, null, actor)).existingEncounter()).isTrue();
        assertThat(encounterRepository.findByPatientId(patientId)).hasSize(2);
        List<String> transitions = jdbcTemplate.queryForList("""
                SELECT from_state || ' -> ' || to_state FROM encounter_event
                WHERE encounter_id = :id AND from_state IS NOT NULL
                """, new MapSqlParameterSource("id", previous.id()), String.class);
        assertThat(transitions).containsExactlyInAnyOrder(
                "CONSULT_COMPLETE -> PENDING_SETTLEMENT", "PENDING_SETTLEMENT -> CLOSED");
    }

    // 05-receptionist.md ⚠7: "An impatient patient clicked through twice
    // must not produce a duplicate queue entry or a second consultation
    // charge." Fires two real concurrent transactions against the same
    // patient_id — the partial unique index is the actual guard; this
    // proves OpenEncounterService recovers from losing that race instead of
    // surfacing an error (root CLAUDE.md "Concurrency").
    @Test
    void concurrentCheckInsForTheSamePatientProduceExactlyOneOpenEncounter() throws Exception {
        UUID patientId = insertFixturePatient();
        UUID departmentId = insertFixtureDepartment();
        UUID actor = bootstrapAdminId();

        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<Encounter>> futures = IntStream.range(0, racers)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return openEncounterService.checkIn(
                                new OpenEncounterCommand(patientId, departmentId, null, actor));
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<UUID> resultIds = futures.stream()
                    .map(this::resolve)
                    .map(Encounter::id)
                    .collect(Collectors.toList());

            assertThat(resultIds).hasSize(racers);
            assertThat(resultIds).as("every racer must resolve to the same encounter").containsOnly(resultIds.get(0));
        } finally {
            pool.shutdown();
        }

        Integer encounterCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM encounter WHERE patient_id = :patientId",
                new MapSqlParameterSource("patientId", patientId), Integer.class);
        assertThat(encounterCount).isEqualTo(1);

        Integer eventCount = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM encounter_event ee
                        JOIN encounter e ON e.id = ee.encounter_id
                        WHERE e.patient_id = :patientId
                        """,
                new MapSqlParameterSource("patientId", patientId), Integer.class);
        assertThat(eventCount).as("exactly one creation event, not one per racer").isEqualTo(1);
    }

    @Test
    void resolvesPayerTypeFromAnActiveInsurancePolicy() {
        UUID patientId = insertFixturePatient();
        UUID departmentId = insertFixtureDepartment();
        UUID schemeId = insertFixtureInsuranceScheme();
        jdbcTemplate.update("""
                        INSERT INTO insurance_policy (patient_id, scheme_id, policy_number, valid_from, valid_to)
                        VALUES (:patientId, :schemeId, 'POL-1', :validFrom, :validTo)
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("schemeId", schemeId)
                        .addValue("validFrom", LocalDate.now().minusDays(1))
                        .addValue("validTo", LocalDate.now().plusYears(1)));

        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patientId, departmentId, null, bootstrapAdminId()));

        assertThat(encounter.payerType()).isEqualTo(PayerType.INSURANCE);
    }

    private Encounter resolve(Future<Encounter> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'FEMALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
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

    private UUID insertFixtureInsuranceScheme() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO insurance_scheme (code, name) VALUES (:code, 'Test Scheme') RETURNING id
                        """,
                new MapSqlParameterSource("code", "SCHEME-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
