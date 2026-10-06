package com.adags.modules.encounters;

import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriageAssessment;
import com.adags.modules.encounters.domain.TriageAssessmentRepository;
import com.adags.modules.encounters.domain.TriagePriority;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V11__triage_assessment.sql. TriageAssessmentTest covers TriageAssessment.
// create()'s own validation; this proves the CHECK constraints mirroring
// that validation are real (root CLAUDE.md "Concurrency": a database
// constraint, not only an application-layer check) by hitting them with raw
// SQL that bypasses the domain factory entirely.
class TriageAssessmentRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private TriageAssessmentRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE triage_assessment, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void insertAndFindRoundTripsAllFields() {
        UUID encounterId = insertFixtureEncounter();

        TriageAssessment toInsert = TriageAssessment.create(
                encounterId, "Fever and headache", "Started this morning", "NKDA", "None",
                ModeOfAmbulation.WALKING, true, 6, "forehead",
                false, null, true,
                false, null, null,
                TriagePriority.URGENT, null, null,
                "Patient anxious", null, bootstrapAdminId());

        TriageAssessment saved = repository.insert(toInsert);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.assessedAt()).isNotNull();

        TriageAssessment reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.chiefComplaint()).isEqualTo("Fever and headache");
        assertThat(reloaded.painScore()).isEqualTo(6);
        assertThat(reloaded.painLocation()).isEqualTo("forehead");
        assertThat(reloaded.fallScore()).isNull();
        assertThat(reloaded.infectionRisk()).isTrue();
        assertThat(reloaded.priority()).isEqualTo(TriagePriority.URGENT);
        assertThat(reloaded.notes()).isEqualTo("Patient anxious");
    }

    @Test
    void immediatePriorityWithReasonAndPregnancyFieldsRoundTrip() {
        UUID encounterId = insertFixtureEncounter();

        TriageAssessment toInsert = TriageAssessment.create(
                encounterId, "Severe bleeding", "Post-partum haemorrhage", "NKDA", "None",
                ModeOfAmbulation.STRETCHER_ASSISTED, false, null, null,
                true, 8, false,
                true, PregnancyStatus.YES, 32,
                TriagePriority.IMMEDIATE, ImmediateReason.ACTIVE_UNCONTROLLED_BLEEDING, null,
                null, null, bootstrapAdminId());

        TriageAssessment saved = repository.insert(toInsert);
        TriageAssessment reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.priority()).isEqualTo(TriagePriority.IMMEDIATE);
        assertThat(reloaded.immediateReason()).isEqualTo(ImmediateReason.ACTIVE_UNCONTROLLED_BLEEDING);
        assertThat(reloaded.pregnancyStatus()).isEqualTo(PregnancyStatus.YES);
        assertThat(reloaded.gestationalWeeks()).isEqualTo(32);
        assertThat(reloaded.fallRisk()).isTrue();
        assertThat(reloaded.fallScore()).isEqualTo(8);
    }

    // §1: "Corrections create an amendment (supersedes_id), never an in-place edit."
    @Test
    void supersedesIdLinksAnAmendmentToTheOriginal() {
        UUID encounterId = insertFixtureEncounter();
        TriageAssessment original = repository.insert(minimalAssessment(encounterId, null));

        TriageAssessment amendment = repository.insert(minimalAssessment(encounterId, original.id()));

        assertThat(amendment.supersedesId()).isEqualTo(original.id());
        assertThat(repository.findByEncounterId(encounterId)).hasSize(2);
    }

    @Test
    void findByEncounterIdReturnsNewestFirst() {
        UUID encounterId = insertFixtureEncounter();
        UUID first = repository.insert(minimalAssessment(encounterId, null)).id();
        UUID second = repository.insert(minimalAssessment(encounterId, first)).id();

        List<TriageAssessment> results = repository.findByEncounterId(encounterId);

        assertThat(results).extracting(TriageAssessment::id).containsExactly(second, first);
    }

    @Test
    void checkConstraintAllowsHasPainTrueWithNoPainScore() {
        UUID encounterId = insertFixtureEncounter();

        rawInsert(encounterId, Map.of(
                "has_pain", "true", "pain_score", "NULL", "pain_location", "'forehead'"));

        assertThat(repository.findByEncounterId(encounterId)).singleElement().satisfies(assessment -> {
            assertThat(assessment.hasPain()).isTrue();
            assertThat(assessment.painScore()).isNull();
            assertThat(assessment.painLocation()).isEqualTo("forehead");
        });
    }

    @Test
    void checkConstraintRejectsHasPainTrueWithNoPainLocation() {
        UUID encounterId = insertFixtureEncounter();

        assertThatThrownBy(() -> rawInsert(encounterId, Map.of(
                "has_pain", "true", "pain_score", "NULL", "pain_location", "NULL")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void checkConstraintRejectsPainScoresOutsideZeroToTen() {
        UUID encounterId = insertFixtureEncounter();

        for (String score : List.of("-1", "11")) {
            assertThatThrownBy(() -> rawInsert(encounterId, Map.of(
                    "has_pain", "true", "pain_score", score, "pain_location", "'forehead'")))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void checkConstraintRejectsHasPainFalseWithAPainScore() {
        UUID encounterId = insertFixtureEncounter();

        assertThatThrownBy(() -> rawInsert(encounterId, Map.of(
                "has_pain", "false", "pain_score", "5", "pain_location", "'forehead'")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void checkConstraintRejectsImmediatePriorityWithNoReason() {
        UUID encounterId = insertFixtureEncounter();

        assertThatThrownBy(() -> rawInsert(encounterId, Map.of("priority", "'IMMEDIATE'", "immediate_reason", "NULL")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void checkConstraintRejectsANonImmediatePriorityWithAReason() {
        UUID encounterId = insertFixtureEncounter();

        assertThatThrownBy(() -> rawInsert(encounterId, Map.of(
                "priority", "'URGENT'", "immediate_reason", "'ACTIVE_CONVULSION'")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void checkConstraintRejectsOtherReasonWithNoFreeText() {
        UUID encounterId = insertFixtureEncounter();

        assertThatThrownBy(() -> rawInsert(encounterId, Map.of(
                "priority", "'IMMEDIATE'", "immediate_reason", "'OTHER'", "immediate_reason_other_text", "NULL")))
                .isInstanceOf(DataAccessException.class);
    }

    private TriageAssessment minimalAssessment(UUID encounterId, UUID supersedesId) {
        return TriageAssessment.create(
                encounterId, "Fever", "Since yesterday", "NKDA", "None",
                ModeOfAmbulation.WALKING, false, null, null,
                false, null, false,
                false, null, null,
                TriagePriority.NON_URGENT, null, null,
                null, supersedesId, bootstrapAdminId());
    }

    // Builds a raw INSERT with sensible defaults for every NOT NULL column,
    // overridden by `overrides`, so each CHECK-constraint test only states
    // the columns relevant to the constraint under test.
    private void rawInsert(UUID encounterId, Map<String, String> overrides) {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("chief_complaint", "'Fever'");
        columns.put("current_symptoms", "'Since yesterday'");
        columns.put("allergies", "'NKDA'");
        columns.put("comorbidities", "'None'");
        columns.put("mode_of_ambulation", "'WALKING'");
        columns.put("has_pain", "false");
        columns.put("pain_score", "NULL");
        columns.put("pain_location", "NULL");
        columns.put("fall_risk", "false");
        columns.put("fall_score", "NULL");
        columns.put("infection_risk", "false");
        columns.put("priority", "'NON_URGENT'");
        columns.put("immediate_reason", "NULL");
        columns.put("immediate_reason_other_text", "NULL");
        columns.putAll(overrides);

        String columnList = String.join(", ", columns.keySet());
        String valueList = String.join(", ", columns.values());

        jdbcTemplate.update(
                "INSERT INTO triage_assessment (encounter_id, assessed_by, " + columnList + ") "
                        + "VALUES (:encounterId, :assessedBy, " + valueList + ")",
                new MapSqlParameterSource()
                        .addValue("encounterId", encounterId)
                        .addValue("assessedBy", bootstrapAdminId()));
    }

    private UUID insertFixtureEncounter() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'FEMALE', '+255700000000')
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

        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'AWAITING_TRIAGE', 'CASH',
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
