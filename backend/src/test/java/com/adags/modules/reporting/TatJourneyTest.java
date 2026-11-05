package com.adags.modules.reporting;

import com.adags.modules.reporting.application.TatAnalyticsService;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class TatJourneyTest extends IntegrationTestSupport {
    private static final UUID FACILITY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired TatAnalyticsService service;

    @Test
    void measuresTriageAndDoctorWithoutInventingVisitCompletion() {
        UUID encounter = encounter();
        Instant origin = Instant.parse("2026-01-05T05:00:00Z");
        UUID actor = admin();
        event(encounter, null, "AWAITING_TRIAGE", origin, actor);
        jdbc.update("""
                INSERT INTO tat_workflow_event (encounter_id, workflow_type, workflow_reference_id, event_type, occurred_at, recorded_by)
                VALUES (:encounter, 'TRIAGE', :encounter, 'STARTED', :started, :actor)
                """, new MapSqlParameterSource().addValue("encounter", encounter)
                .addValue("started", origin.plusSeconds(12 * 60)).addValue("actor", actor));
        jdbc.update("""
                INSERT INTO triage_assessment (encounter_id, chief_complaint, current_symptoms, allergies,
                  comorbidities, mode_of_ambulation, has_pain, fall_risk, infection_risk, priority,
                  assessed_by, assessed_at)
                VALUES (:encounter, 'Fever', 'Fever', 'None', 'None', 'WALKING', false, false, false,
                  'NON_URGENT', :actor, :completed)
                """, new MapSqlParameterSource().addValue("encounter", encounter).addValue("actor", actor)
                .addValue("completed", origin.plusSeconds(20 * 60)));
        event(encounter, "AWAITING_TRIAGE", "AWAITING_CONSULT", origin.plusSeconds(20 * 60), actor);
        event(encounter, "AWAITING_CONSULT", "IN_CONSULT", origin.plusSeconds(35 * 60), actor);
        event(encounter, "IN_CONSULT", "AWAITING_RESULTS", origin.plusSeconds(45 * 60), actor);

        var journey = service.encounterJourney(FACILITY, encounter);
        var triage = journey.stages().stream().filter(s -> s.workflowType().equals("TRIAGE")).findFirst().orElseThrow();
        var doctor = journey.stages().stream().filter(s -> s.workflowType().equals("DOCTOR")).findFirst().orElseThrow();
        assertThat(triage.waitingSeconds()).isEqualTo(12 * 60L);
        assertThat(triage.serviceSeconds()).isEqualTo(8 * 60L);
        assertThat(triage.totalTatSeconds()).isEqualTo(20 * 60L);
        assertThat(doctor.waitingSeconds()).isEqualTo(15 * 60L);
        assertThat(doctor.serviceSeconds()).isEqualTo(10 * 60L);
        assertThat(doctor.status()).isEqualTo("AWAITING_RESULTS");
        assertThat(journey.visit().visitSeconds()).isNull();
        assertThat(journey.totalRecordedWaitingSeconds()).isEqualTo(27 * 60.0);
    }

    @Test
    void databaseRejectsDuplicateStarts() {
        UUID encounter = encounter(), actor = admin();
        var p = new MapSqlParameterSource().addValue("encounter", encounter).addValue("actor", actor);
        String sql = """
                INSERT INTO tat_workflow_event (encounter_id, workflow_type, workflow_reference_id, event_type, recorded_by)
                VALUES (:encounter, 'TRIAGE', :encounter, 'STARTED', :actor)
                """;
        jdbc.update(sql, p);
        assertThatThrownBy(() -> jdbc.update(sql, p)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID encounter() {
        UUID patient = jdbc.queryForObject("""
                INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                VALUES (:number, 'Tat', 'Test', DATE '1990-01-01', 'MALE', '+255700000000') RETURNING id
                """, new MapSqlParameterSource("number", "TAT-" + UUID.randomUUID()), UUID.class);
        UUID department = jdbc.queryForObject("""
                INSERT INTO department (facility_id, code, name) VALUES (:facility, :code, 'TAT Test') RETURNING id
                """, new MapSqlParameterSource().addValue("facility", FACILITY)
                .addValue("code", "TAT-" + UUID.randomUUID().toString().substring(0, 8)), UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                VALUES (:patient, :facility, :department, 'OUTPATIENT', 'AWAITING_RESULTS', 'CASH', :actor) RETURNING id
                """, new MapSqlParameterSource().addValue("patient", patient).addValue("facility", FACILITY)
                .addValue("department", department).addValue("actor", admin()), UUID.class);
    }

    private UUID admin() {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }

    private void event(UUID encounter, String from, String to, Instant when, UUID actor) {
        jdbc.update("""
                INSERT INTO encounter_event (encounter_id, from_state, to_state, occurred_at, actor_user_id)
                VALUES (:encounter, :from, :to, :when, :actor)
                """, new MapSqlParameterSource().addValue("encounter", encounter).addValue("from", from)
                .addValue("to", to).addValue("when", when).addValue("actor", actor));
    }
}
