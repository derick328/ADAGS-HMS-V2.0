package com.adags.modules.encounters;

import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 05-receptionist.md §8's "here now" panel and the admin dashboard's
// "Waiting for triage"/"In progress today" tiles.
class EncounterSearchTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private EncounterRepository encounterRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE encounter, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void filtersByState() {
        // Two different patients — idx_encounter_one_open_per_patient
        // forbids two non-terminal encounters for the same one.
        UUID awaitingTriage = insertFixtureEncounter(insertFixturePatient(), "AWAITING_TRIAGE");
        UUID inConsult = insertFixtureEncounter(insertFixturePatient(), "IN_CONSULT");

        List<Encounter> results = encounterRepository.search(List.of(EncounterState.AWAITING_TRIAGE), false, null, null);

        assertThat(results).extracting(Encounter::id).containsExactly(awaitingTriage);
        assertThat(results).extracting(Encounter::id).doesNotContain(inConsult);
    }

    @Test
    void excludeTerminalDropsClosedEncounters() {
        UUID patientId = insertFixturePatient();
        UUID open = insertFixtureEncounter(patientId, "IN_CONSULT");
        UUID closed = insertFixtureEncounter(patientId, "CLOSED");

        List<Encounter> results = encounterRepository.search(null, true, null, null);

        assertThat(results).extracting(Encounter::id).contains(open);
        assertThat(results).extracting(Encounter::id).doesNotContain(closed);
    }

    @Test
    void filtersByCreatedRange() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId, "AWAITING_TRIAGE");
        Encounter encounter = encounterRepository.findById(encounterId).orElseThrow();

        List<Encounter> withinRange = encounterRepository.search(
                null, false, encounter.createdAt().minusSeconds(60), encounter.createdAt().plusSeconds(60));
        List<Encounter> outsideRange = encounterRepository.search(
                null, false, encounter.createdAt().plusSeconds(3600), encounter.createdAt().plusSeconds(7200));

        assertThat(withinRange).extracting(Encounter::id).contains(encounterId);
        assertThat(outsideRange).extracting(Encounter::id).doesNotContain(encounterId);
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID insertFixtureEncounter(UUID patientId, String state) {
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
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', :state, 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId)
                        .addValue("state", state),
                UUID.class);
    }
}
