package com.adags.modules.encounters;

import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.encounters.application.MissingReferralException;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.application.SubmitTriageAssessmentCommand;
import com.adags.modules.encounters.application.SubmitTriageAssessmentService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.EncounterType;
import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.StaleEncounterException;
import com.adags.modules.encounters.domain.TriagePriority;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 06-triage.md §6 + 02-billing-architecture.md §4.1: real Spring context,
// real Postgres, real BillingFacadeImpl. Billing is real now (Phase 3) —
// these two tests assign the bootstrap admin as the "provider," whose only
// role (ADMIN) has no ProviderBillingCategory mapping (V19), so pricing
// resolution genuinely fails and step 6 still rolls the whole transaction
// back, proving atomicity survives with real billing wired in. The real happy path
// (a properly role-assigned provider, a real charge, real payment) is
// EndToEndBillingTest, not here — this file stays focused on the
// encounters-module transition/rollback mechanics.
// SubmitTriageAssessmentServiceTest (Mockito) proves the ordering the
// mechanism relies on; this proves the mechanism's actual, current,
// end-to-end behaviour against a real database.
class SubmitTriageAssessmentIntegrationTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private OpenEncounterService openEncounterService;

    @Autowired
    private SubmitTriageAssessmentService submitTriageAssessmentService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE payment_bypass, encounter_event, triage_assessment, vital_reading, vital_signs,
                                 allergy_record, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void immediateSubmissionRollsBackEverythingWhenTheProviderHasNoClinicalRole() {
        UUID patientId = insertFixturePatient();
        Encounter encounter = openEncounter(patientId);
        UUID providerId = bootstrapAdminId();

        SubmitTriageAssessmentCommand command = command(encounter, providerId, TriagePriority.IMMEDIATE,
                ImmediateReason.ACTIVE_UNCONTROLLED_BLEEDING);

        assertThatThrownBy(() -> submitTriageAssessmentService.submit(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("holds no role mapped to a consultation price");

        assertNothingPersistedBeyondCheckIn(encounter, patientId);
    }

    @Test
    void nonImmediateSubmissionAlsoRollsBackEverythingWhenTheProviderHasNoClinicalRole() {
        UUID patientId = insertFixturePatient();
        Encounter encounter = openEncounter(patientId);
        UUID providerId = bootstrapAdminId();

        SubmitTriageAssessmentCommand command = command(encounter, providerId, TriagePriority.URGENT, null);

        assertThatThrownBy(() -> submitTriageAssessmentService.submit(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("holds no role mapped to a consultation price");

        assertNothingPersistedBeyondCheckIn(encounter, patientId);

        Integer bypassCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_bypass WHERE encounter_id = :id",
                new MapSqlParameterSource("id", encounter.id()), Integer.class);
        assertThat(bypassCount).as("URGENT must never even attempt a bypass").isEqualTo(0);
    }

    @Test
    void missingReferralIsRejectedWithoutTouchingTheDatabase() {
        UUID patientId = insertFixturePatient();
        Encounter encounter = openEncounter(patientId);

        SubmitTriageAssessmentCommand command = command(encounter, null, TriagePriority.NON_URGENT, null);

        assertThatThrownBy(() -> submitTriageAssessmentService.submit(command))
                .isInstanceOf(MissingReferralException.class);

        assertNothingPersistedBeyondCheckIn(encounter, patientId);
    }

    // root CLAUDE.md "Concurrency": a second nurse submitting against a
    // stale read must fail with a reload prompt, not silently overwrite.
    @Test
    void aStaleExpectedVersionIsRejected() {
        UUID patientId = insertFixturePatient();
        Encounter encounter = openEncounter(patientId);
        UUID providerId = bootstrapAdminId();

        SubmitTriageAssessmentCommand staleCommand = new SubmitTriageAssessmentCommand(
                encounter.id(), encounter.version() + 1, providerId,
                "Fever", "Since yesterday", "NKDA", "None", ModeOfAmbulation.WALKING,
                false, null, null, false, null, false, null, null,
                TriagePriority.NON_URGENT, null, null, null, null,
                36.8, 120, 80, 75, 16, 98, 70, 175, "ALERT", null,
                providerId, Instant.now());

        assertThatThrownBy(() -> submitTriageAssessmentService.submit(staleCommand))
                .isInstanceOf(StaleEncounterException.class);

        assertNothingPersistedBeyondCheckIn(encounter, patientId);
    }

    @Test
    void anEncounterNotInAwaitingTriageIsRejected() {
        UUID patientId = insertFixturePatient();
        Encounter encounter = openEncounter(patientId);
        UUID providerId = bootstrapAdminId();

        jdbcTemplate.update("UPDATE encounter SET state = 'CLOSED' WHERE id = :id",
                new MapSqlParameterSource("id", encounter.id()));

        SubmitTriageAssessmentCommand command = command(encounter, providerId, TriagePriority.NON_URGENT, null);

        assertThatThrownBy(() -> submitTriageAssessmentService.submit(command))
                .isInstanceOf(InvalidEncounterTransitionException.class);
    }

    private void assertNothingPersistedBeyondCheckIn(Encounter encounter, UUID patientId) {
        Encounter reloaded = jdbcTemplate.queryForObject(
                "SELECT state, version, assigned_provider_id, type FROM encounter WHERE id = :id",
                new MapSqlParameterSource("id", encounter.id()),
                (rs, rowNum) -> new Encounter(null, null, null, null, null,
                        (UUID) rs.getObject("assigned_provider_id"),
                        EncounterType.valueOf(rs.getString("type")),
                        EncounterState.valueOf(rs.getString("state")),
                        null, rs.getInt("version"), null, null));
        assertThat(reloaded.state()).isEqualTo(EncounterState.AWAITING_TRIAGE);
        assertThat(reloaded.version()).isEqualTo(encounter.version());
        assertThat(reloaded.assignedProviderId()).isNull();

        assertThat(countWhere("triage_assessment", "encounter_id", encounter.id())).isZero();
        assertThat(countWhere("vital_signs", "encounter_id", encounter.id())).isZero();
        assertThat(countWhere("payment_bypass", "encounter_id", encounter.id())).isZero();
        assertThat(countWhere("allergy_record", "patient_id", patientId)).isZero();

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM encounter_event WHERE encounter_id = :id",
                new MapSqlParameterSource("id", encounter.id()), Integer.class);
        assertThat(eventCount).as("only the check-in event, nothing from the failed submission").isEqualTo(1);
    }

    private int countWhere(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = :value",
                new MapSqlParameterSource("value", value), Integer.class);
    }

    private SubmitTriageAssessmentCommand command(
            Encounter encounter, UUID providerId, TriagePriority priority, ImmediateReason immediateReason
    ) {
        return new SubmitTriageAssessmentCommand(
                encounter.id(), encounter.version(), providerId,
                "Fever and headache", "Since yesterday", "NKDA", "None", ModeOfAmbulation.WALKING,
                false, null, null,
                false, null,
                false,
                null, null,
                priority, immediateReason, immediateReason == ImmediateReason.OTHER ? "n/a" : null,
                null, null,
                36.8, 120, 80, 75, 16, 98, 70, 175, "ALERT", null,
                providerId == null ? bootstrapAdminId() : providerId, Instant.now());
    }

    private Encounter openEncounter(UUID patientId) {
        UUID departmentId = insertFixtureDepartment();
        return openEncounterService.checkIn(
                new OpenEncounterCommand(patientId, departmentId, null, bootstrapAdminId()));
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
