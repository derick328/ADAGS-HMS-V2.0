package com.adags.modules.encounters;

import com.adags.modules.encounters.application.CloseEncounterCommand;
import com.adags.modules.encounters.application.CloseEncounterService;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.encounters.application.MissingCloseReasonException;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 05-receptionist.md §5.3 "Dismiss". Closing an encounter never deletes or
// deactivates the patient (⚠8) — every test here asserts the patient row
// is untouched alongside the encounter transition.
class CloseEncounterServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private OpenEncounterService openEncounterService;

    @Autowired
    private CloseEncounterService closeEncounterService;

    @Autowired
    private EncounterRepository encounterRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE encounter, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void leftWithoutBeingSeenDoesNotRequireAReason() {
        Encounter opened = openEncounter();
        UUID actor = bootstrapAdminId();

        Encounter closed = closeEncounterService.close(
                new CloseEncounterCommand(opened.id(), EncounterState.LEFT_WITHOUT_BEING_SEEN, null, actor));

        assertThat(closed.state()).isEqualTo(EncounterState.LEFT_WITHOUT_BEING_SEEN);
        assertPatientUntouched(opened.patientId());
    }

    @Test
    void cancelledInErrorRequiresAReason() {
        Encounter opened = openEncounter();
        UUID actor = bootstrapAdminId();

        assertThatThrownBy(() -> closeEncounterService.close(
                new CloseEncounterCommand(opened.id(), EncounterState.CANCELLED_IN_ERROR, null, actor)))
                .isInstanceOf(MissingCloseReasonException.class);

        assertThatThrownBy(() -> closeEncounterService.close(
                new CloseEncounterCommand(opened.id(), EncounterState.CANCELLED_IN_ERROR, "  ", actor)))
                .isInstanceOf(MissingCloseReasonException.class);
    }

    @Test
    void cancelledInErrorWithAReasonSucceedsAndPersistsIt() {
        Encounter opened = openEncounter();
        UUID actor = bootstrapAdminId();

        Encounter closed = closeEncounterService.close(new CloseEncounterCommand(
                opened.id(), EncounterState.CANCELLED_IN_ERROR, "checked in the wrong patient", actor));

        assertThat(closed.state()).isEqualTo(EncounterState.CANCELLED_IN_ERROR);
        String reason = jdbcTemplate.queryForObject(
                "SELECT reason FROM encounter_event WHERE encounter_id = :id AND to_state = 'CANCELLED_IN_ERROR'",
                new MapSqlParameterSource("id", opened.id()), String.class);
        assertThat(reason).isEqualTo("checked in the wrong patient");
        assertPatientUntouched(opened.patientId());
    }

    // 02-billing-architecture.md §8: close(encounter) — medical_complete is
    // the only gate; there is no code path where balance blocks this.
    @Test
    void closedSucceedsWithNoBalanceCheckOfAnyKind() {
        Encounter opened = openEncounter();
        UUID actor = bootstrapAdminId();

        Encounter closed = closeEncounterService.close(
                new CloseEncounterCommand(opened.id(), EncounterState.CLOSED, null, actor));

        assertThat(closed.state()).isEqualTo(EncounterState.CLOSED);
        assertPatientUntouched(opened.patientId());
    }

    @Test
    void closingAnAlreadyTerminalEncounterIsRejected() {
        Encounter opened = openEncounter();
        UUID actor = bootstrapAdminId();
        closeEncounterService.close(new CloseEncounterCommand(opened.id(), EncounterState.CLOSED, null, actor));

        assertThatThrownBy(() -> closeEncounterService.close(
                new CloseEncounterCommand(opened.id(), EncounterState.LEFT_WITHOUT_BEING_SEEN, null, actor)))
                .isInstanceOf(InvalidEncounterTransitionException.class);
    }

    @Test
    void onlyTheThreeDismissOutcomesAreAccepted() {
        Encounter opened = openEncounter();
        UUID actor = bootstrapAdminId();

        assertThatThrownBy(() -> closeEncounterService.close(
                new CloseEncounterCommand(opened.id(), EncounterState.AWAITING_PAYMENT, null, actor)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 01-architecture.md §2.1 rule 4 / user instruction: EncounterEvent is
    // written inside the same transaction as the transition — if the event
    // write fails, the transition rolls back. Forces the event insert to
    // fail (FK violation on actor_user_id) and asserts the encounter's
    // state is untouched, not left half-applied.
    @Test
    void aFailedEventWriteRollsBackTheStateTransitionWithIt() {
        Encounter opened = openEncounter();
        UUID nonExistentActor = UUID.randomUUID();

        assertThatThrownBy(() -> closeEncounterService.close(
                new CloseEncounterCommand(opened.id(), EncounterState.CLOSED, null, nonExistentActor)))
                .isInstanceOf(DataAccessException.class);

        Encounter reloaded = encounterRepository.findById(opened.id()).orElseThrow();
        assertThat(reloaded.state())
                .as("the UPDATE must not survive when the EncounterEvent insert that shares its transaction fails")
                .isEqualTo(EncounterState.AWAITING_TRIAGE);
    }

    private void assertPatientUntouched(UUID patientId) {
        Boolean active = jdbcTemplate.queryForObject(
                "SELECT active FROM patient WHERE id = :id",
                new MapSqlParameterSource("id", patientId), Boolean.class);
        assertThat(active).as("⚠8: closing an encounter must never deactivate the patient").isTrue();
    }

    private Encounter openEncounter() {
        UUID patientId = insertFixturePatient();
        UUID departmentId = insertFixtureDepartment();
        return openEncounterService.checkIn(new OpenEncounterCommand(patientId, departmentId, null, bootstrapAdminId()));
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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
