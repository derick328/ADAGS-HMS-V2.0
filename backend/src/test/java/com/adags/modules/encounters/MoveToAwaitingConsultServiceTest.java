package com.adags.modules.encounters;

import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.BillingGateRejectedException;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.encounters.application.MoveToAwaitingConsultService;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.application.SubmitTriageAssessmentCommand;
import com.adags.modules.encounters.application.SubmitTriageAssessmentService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.TriagePriority;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Row 7 of the encounter transition table. 02-billing-architecture.md
// §2.1/§3: consultation entry is the one deliberate payment gate.
class MoveToAwaitingConsultServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private OpenEncounterService openEncounterService;

    @Autowired
    private SubmitTriageAssessmentService submitTriageAssessmentService;

    @Autowired
    private MoveToAwaitingConsultService moveToAwaitingConsultService;

    @Autowired
    private OpenCashierShiftService openCashierShiftService;

    @Autowired
    private PostPaymentService postPaymentService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, payment_bypass, encounter_event, triage_assessment, vital_reading,
                                 vital_signs, allergy_record, outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM role_assignment WHERE role_id = (SELECT id FROM role WHERE code = 'DOCTOR')",
                new MapSqlParameterSource());
    }

    @Test
    void refusedWhileTheConsultationLineIsUnpaid() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        UUID patientId = insertFixturePatient();
        Encounter encounter = openEncounter(patientId);
        submitTriageAssessmentService.submit(triageCommand(encounter, doctorId, TriagePriority.NON_URGENT));

        assertThatThrownBy(() -> moveToAwaitingConsultService.moveToAwaitingConsult(encounter.id(), admin))
                .isInstanceOf(BillingGateRejectedException.class);

        String state = jdbcTemplate.queryForObject("SELECT state FROM encounter WHERE id = :id",
                new MapSqlParameterSource("id", encounter.id()), String.class);
        assertThat(state).isEqualTo(EncounterState.AWAITING_PAYMENT.name());
    }

    @Test
    void succeedsOnceTheConsultationLineIsPaidInFull() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        UUID patientId = insertFixturePatient();
        Encounter encounter = openEncounter(patientId);
        submitTriageAssessmentService.submit(triageCommand(encounter, doctorId, TriagePriority.NON_URGENT));

        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        UUID invoiceLineId = jdbcTemplate.queryForObject("""
                        SELECT il.id FROM invoice_line il JOIN invoice i ON i.id = il.invoice_id
                        WHERE i.encounter_id = :encounterId
                        """,
                new MapSqlParameterSource("encounterId", encounter.id()), UUID.class);
        postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(invoiceLineId, 1_000_000)), admin));

        Encounter updated = moveToAwaitingConsultService.moveToAwaitingConsult(encounter.id(), admin);

        assertThat(updated.state()).isEqualTo(EncounterState.AWAITING_CONSULT);
    }

    private SubmitTriageAssessmentCommand triageCommand(Encounter encounter, UUID providerId, TriagePriority priority) {
        return new SubmitTriageAssessmentCommand(
                encounter.id(), encounter.version(), providerId,
                "Fever and headache", "Since yesterday", "NKDA", "None", ModeOfAmbulation.WALKING,
                false, null, null, false, null, false, null, null,
                priority, null, null, null, null,
                36.8, 120, 80, 75, 16, 98, 70, 175, "ALERT", null,
                providerId, Instant.now());
    }

    private Encounter openEncounter(UUID patientId) {
        UUID departmentId = insertFixtureDepartment();
        return openEncounterService.checkIn(
                new OpenEncounterCommand(patientId, departmentId, null, bootstrapAdminId()));
    }

    private UUID insertFixtureDoctor() {
        UUID userId = jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash)
                        VALUES (:username, 'x')
                        RETURNING id
                        """,
                new MapSqlParameterSource("username", "test-doctor-" + UUID.randomUUID()), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = 'DOCTOR'
                        """,
                new MapSqlParameterSource("userId", userId));
        return userId;
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID insertFixtureDepartment() {
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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
