package com.adags.modules.encounters;

import com.adags.modules.clinical.application.AddDiagnosisService;
import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.DiagnosisCertainty;
import com.adags.modules.clinical.domain.InvalidConsultationRecordException;
import com.adags.modules.encounters.application.FinalizeConsultationCommand;
import com.adags.modules.encounters.application.FinalizeConsultationService;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.encounters.application.MarkAwaitingResultsService;
import com.adags.modules.encounters.application.OpenConsultationService;
import com.adags.modules.encounters.domain.DispositionOutcome;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultationLifecycleIntegrationTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private OpenConsultationService openConsultationService;

    @Autowired
    private MarkAwaitingResultsService markAwaitingResultsService;

    @Autowired
    private FinalizeConsultationService finalizeConsultationService;

    @Autowired
    private AddDiagnosisService addDiagnosisService;

    @Autowired
    private ConsultationRecordRepository consultationRecordRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE diagnosis, consultation_record, encounter_event, encounter, patient,
                                 icd10_code CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void openTransitionsAwaitingConsultToInConsultAndCreatesAnOpenRecord() {
        UUID encounterId = insertFixtureEncounter("AWAITING_CONSULT");
        UUID doctorId = bootstrapAdminId();

        UUID consultationRecordId = openConsultationService.open(encounterId, doctorId);

        assertThat(consultationRecordId).isNotNull();
        assertThat(currentState(encounterId)).isEqualTo(EncounterState.IN_CONSULT);
        assertThat(consultationRecordRepository.findOpenByEncounterAndAuthor(encounterId, doctorId)).isPresent();
    }

    // The encounter-level transition is NOT repeatable — once IN_CONSULT,
    // a second open() is a transition-graph violation, matching every other
    // encounter transition in this codebase. The resumable half lives one
    // layer down: ClinicalFacade.openConsultationRecord returns the same
    // OPEN ConsultationRecord id if called again for the same pair, which
    // is what lets a doctor's crashed browser tab reload into the same
    // draft rather than losing it — it just isn't reached through this
    // service a second time once the encounter has already moved.
    @Test
    void aSecondOpenCallFailsOnceTheEncounterHasAlreadyMovedToInConsult() {
        UUID encounterId = insertFixtureEncounter("AWAITING_CONSULT");
        UUID doctorId = bootstrapAdminId();

        UUID first = openConsultationService.open(encounterId, doctorId);

        assertThatThrownBy(() -> openConsultationService.open(encounterId, doctorId))
                .isInstanceOf(InvalidEncounterTransitionException.class);
        assertThat(first).isNotNull();
    }

    @Test
    void openOnAnEncounterNotAwaitingConsultIsRejected() {
        UUID encounterId = insertFixtureEncounter("AWAITING_TRIAGE");

        assertThatThrownBy(() -> openConsultationService.open(encounterId, bootstrapAdminId()))
                .isInstanceOf(InvalidEncounterTransitionException.class);
    }

    @Test
    void markAwaitingResultsTransitionsInConsultToAwaitingResults() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");

        markAwaitingResultsService.markAwaitingResults(encounterId, bootstrapAdminId());

        assertThat(currentState(encounterId)).isEqualTo(EncounterState.AWAITING_RESULTS);
    }

    @Test
    void finalizeToConsultCompleteTransitionsTheEncounterAndTheNote() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        UUID consultationRecordId = openAndFillConsultationRecord(encounterId, doctorId);
        addPrimaryDiagnosis(encounterId, consultationRecordId, doctorId);

        Encounter result = finalizeConsultationService.finalize(
                new FinalizeConsultationCommand(encounterId, doctorId, DispositionOutcome.CONSULT_COMPLETE, false, null));

        assertThat(result.state()).isEqualTo(EncounterState.CONSULT_COMPLETE);
        assertThat(currentState(encounterId)).isEqualTo(EncounterState.CONSULT_COMPLETE);
    }

    @Test
    void finalizeToDeceasedTransitionsTheEncounter() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        UUID consultationRecordId = openAndFillConsultationRecord(encounterId, doctorId);
        addPrimaryDiagnosis(encounterId, consultationRecordId, doctorId);

        Encounter result = finalizeConsultationService.finalize(
                new FinalizeConsultationCommand(encounterId, doctorId, DispositionOutcome.DECEASED, false, null));

        assertThat(result.state()).isEqualTo(EncounterState.DECEASED);
    }

    // The core invariant: if the note doesn't validate, the encounter must
    // not move — same reasoning as the triage submission's billing step.
    @Test
    void finalizeRollsBackTheEncounterWhenTheNoteFailsValidation() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        // Opened but left blank — no history, no exam, no diagnosis.
        consultationRecordRepository.insert(ConsultationRecord.open(encounterId, doctorId));

        assertThatThrownBy(() -> finalizeConsultationService.finalize(
                new FinalizeConsultationCommand(encounterId, doctorId, DispositionOutcome.CONSULT_COMPLETE, false, null)))
                .isInstanceOf(InvalidConsultationRecordException.class);

        assertThat(currentState(encounterId)).isEqualTo(EncounterState.IN_CONSULT);
    }

    @Test
    void finalizeOnAnEncounterNotInConsultIsRejected() {
        UUID encounterId = insertFixtureEncounter("AWAITING_CONSULT");

        assertThatThrownBy(() -> finalizeConsultationService.finalize(
                new FinalizeConsultationCommand(encounterId, bootstrapAdminId(), DispositionOutcome.CONSULT_COMPLETE, false, null)))
                .isInstanceOf(InvalidEncounterTransitionException.class);
    }

    private UUID openAndFillConsultationRecord(UUID encounterId, UUID doctorId) {
        UUID consultationRecordId = consultationRecordRepository.insert(
                ConsultationRecord.open(encounterId, doctorId)).id();
        consultationRecordRepository.autosave(consultationRecordId, "Fever for 3 days", null, null, null, null,
                "Alert, mild pallor", null, "Awaiting malaria RDT");
        return consultationRecordId;
    }

    private void addPrimaryDiagnosis(UUID encounterId, UUID consultationRecordId, UUID doctorId) {
        jdbcTemplate.update("""
                        INSERT INTO icd10_code (code, title, icd10_release) VALUES ('B50.9', 'P. falciparum malaria, unspecified', '2019')
                        ON CONFLICT (code) DO NOTHING
                        """, new MapSqlParameterSource());
        addDiagnosisService.addDiagnosis(encounterId, consultationRecordId, "B50.9", null,
                DiagnosisCertainty.PROVISIONAL, true, doctorId);
    }

    private EncounterState currentState(UUID encounterId) {
        return EncounterState.valueOf(jdbcTemplate.queryForObject(
                "SELECT state FROM encounter WHERE id = :id",
                new MapSqlParameterSource("id", encounterId), String.class));
    }

    private UUID insertFixtureEncounter(String state) {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
