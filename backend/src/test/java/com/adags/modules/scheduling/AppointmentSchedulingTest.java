package com.adags.modules.scheduling;

import com.adags.modules.scheduling.application.CreateFollowUpAppointmentCommand;
import com.adags.modules.scheduling.application.CreateFollowUpAppointmentService;
import com.adags.modules.scheduling.application.ClinicFollowUpService;
import com.adags.modules.scheduling.domain.Appointment;
import com.adags.modules.scheduling.domain.AppointmentRepository;
import com.adags.modules.scheduling.domain.AppointmentStatus;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 05a-receptionist-decisions.md §3: D1 — the only creation path is a
// doctor's follow-up after a consultation. §3.2/§3.3: the expected-returns
// window and arrival-linking behaviour.
class AppointmentSchedulingTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private CreateFollowUpAppointmentService createFollowUpAppointmentService;

    @Autowired
    private SchedulingFacade schedulingFacade;

    @Autowired
    private ClinicFollowUpService clinicFollowUpService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE appointment, encounter, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void createsAScheduledFollowUp() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID doctor = bootstrapAdminId();
        Instant scheduledAt = Instant.now().plus(3, ChronoUnit.DAYS);

        Appointment appointment = createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                patientId, encounterId, scheduledAt, "review labs", doctor));

        assertThat(appointment.id()).isNotNull();
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointment.providerId()).isEqualTo(doctor);
        assertThat(appointment.attendedEncounterId()).isNull();
    }

    @Test
    void aFollowUpMustBeScheduledInTheFuture() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);

        assertThatThrownBy(() -> createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                patientId, encounterId, Instant.now().minusSeconds(60), null, bootstrapAdminId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 05a §3.2: "remains listed until the encounter resolves" for
    // AWAITING_RESULTS encounters, and for follow-ups: "from 48 hours
    // before the due date." A follow-up due in 10 hours is inside that
    // window; one due in 10 days is not.
    @Test
    void expectedReturnsIncludesOverdueAndWithin48HoursButNotFarFuture() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID doctor = bootstrapAdminId();

        Appointment overdue = createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                patientId, encounterId, Instant.now().plus(1, ChronoUnit.HOURS), null, doctor));
        Appointment withinWindow = createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                patientId, encounterId, Instant.now().plus(30, ChronoUnit.HOURS), null, doctor));
        Appointment farFuture = createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                patientId, encounterId, Instant.now().plus(10, ChronoUnit.DAYS), null, doctor));

        var expectedReturns = appointmentRepository.findExpectedReturns(Instant.now().plus(48, ChronoUnit.HOURS));

        assertThat(expectedReturns).extracting(Appointment::id)
                .contains(overdue.id(), withinWindow.id())
                .doesNotContain(farFuture.id());
    }

    // 05a §3.3: checking in against a follow-up marks it ATTENDED and
    // records which encounter it became.
    @Test
    void markAttendedTransitionsAndRecordsTheNewEncounter() {
        // sourceEncounterId and newEncounterId are deliberately different
        // patients' encounters here — idx_encounter_one_open_per_patient
        // forbids two non-terminal encounters for one patient, and this
        // test is about markAttended's own transition, not encounter
        // lifecycle consistency (OpenEncounterServiceTest's
        // checkInAgainstABookedFollowUpMarksItAttended covers the real,
        // same-patient, source-closed-first path end to end).
        UUID patientId = insertFixturePatient();
        UUID sourceEncounterId = insertFixtureEncounter(patientId);
        UUID newEncounterId = insertFixtureEncounter(insertFixturePatient());
        Appointment appointment = createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                patientId, sourceEncounterId, Instant.now().plus(1, ChronoUnit.DAYS), null, bootstrapAdminId()));

        schedulingFacade.markAttended(appointment.id(), newEncounterId);

        Appointment reloaded = appointmentRepository.findById(appointment.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(AppointmentStatus.ATTENDED);
        assertThat(reloaded.attendedEncounterId()).isEqualTo(newEncounterId);
    }

    // Idempotent against a retried check-in — never re-marks or overwrites
    // which encounter an already-ATTENDED appointment became.
    @Test
    void markAttendedOnAnAlreadyAttendedAppointmentIsANoOp() {
        UUID patientId = insertFixturePatient();
        UUID sourceEncounterId = insertFixtureEncounter(patientId);
        UUID firstEncounterId = insertFixtureEncounter(insertFixturePatient());
        UUID secondEncounterId = insertFixtureEncounter(insertFixturePatient());
        Appointment appointment = createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                patientId, sourceEncounterId, Instant.now().plus(1, ChronoUnit.DAYS), null, bootstrapAdminId()));
        schedulingFacade.markAttended(appointment.id(), firstEncounterId);

        schedulingFacade.markAttended(appointment.id(), secondEncounterId);

        Appointment reloaded = appointmentRepository.findById(appointment.id()).orElseThrow();
        assertThat(reloaded.attendedEncounterId()).isEqualTo(firstEncounterId);
    }

    @Test
    void clinicEnrollmentSurvivesAttendedVisitsAndRejectsDuplicateScheduledReturns() {
        UUID patientId = insertFixturePatient();
        UUID sourceEncounterId = insertFixtureEncounter(patientId);
        UUID departmentId = jdbcTemplate.queryForObject("SELECT department_id FROM encounter WHERE id = :id",
                new MapSqlParameterSource("id", sourceEncounterId), UUID.class);
        UUID doctor = bootstrapAdminId();
        jdbcTemplate.update("UPDATE encounter SET assigned_provider_id = :doctor WHERE id = :id",
                new MapSqlParameterSource().addValue("doctor", doctor).addValue("id", sourceEncounterId));
        Instant due = Instant.now().plus(10, ChronoUnit.DAYS);

        Appointment first = clinicFollowUpService.schedule(patientId, sourceEncounterId, departmentId, "Diabetes", due,
                "Bring glucose log", doctor);
        assertThatThrownBy(() -> clinicFollowUpService.schedule(patientId, sourceEncounterId, departmentId, "Diabetes",
                due.plus(1, ChronoUnit.DAYS), null, doctor)).isInstanceOf(IllegalArgumentException.class);
        UUID attendedEncounterId = insertFixtureEncounter(insertFixturePatient());
        schedulingFacade.markAttended(first.id(), attendedEncounterId);
        Appointment second = clinicFollowUpService.schedule(patientId, sourceEncounterId, departmentId, "Diabetes",
                due.plus(30, ChronoUnit.DAYS), null, doctor);
        assertThat(second.clinicFollowUpId()).isEqualTo(first.clinicFollowUpId());
        assertThat(appointmentRepository.findById(first.id()).orElseThrow().arrivedAt()).isNotNull();
        clinicFollowUpService.close(first.clinicFollowUpId());
        assertThat(appointmentRepository.findById(second.id()).orElseThrow().status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void lateArrivalAfterNoShowPreservesTheMissedAppointmentMarker() {
        UUID patientId = insertFixturePatient();
        UUID sourceEncounterId = insertFixtureEncounter(patientId);
        UUID departmentId = jdbcTemplate.queryForObject("SELECT department_id FROM encounter WHERE id = :id",
                new MapSqlParameterSource("id", sourceEncounterId), UUID.class);
        UUID doctor = bootstrapAdminId();
        jdbcTemplate.update("UPDATE encounter SET assigned_provider_id = :doctor WHERE id = :id",
                new MapSqlParameterSource().addValue("doctor", doctor).addValue("id", sourceEncounterId));
        Appointment appointment = clinicFollowUpService.schedule(patientId, sourceEncounterId, departmentId,
                "General review", Instant.now().plus(1, ChronoUnit.DAYS), null, doctor);
        appointmentRepository.setStatus(appointment.id(), AppointmentStatus.NO_SHOW);
        UUID arrivalEncounterId = insertFixtureEncounter(insertFixturePatient());
        schedulingFacade.validateCheckIn(appointment.id(), patientId, departmentId);
        schedulingFacade.markAttended(appointment.id(), arrivalEncounterId);
        Appointment attended = appointmentRepository.findById(appointment.id()).orElseThrow();
        assertThat(attended.status()).isEqualTo(AppointmentStatus.ATTENDED);
        assertThat(attended.noShowAt()).isNotNull();
        assertThat(attended.arrivedAt()).isNotNull();
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID insertFixtureEncounter(UUID patientId) {
        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);

        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'IN_CONSULT', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
