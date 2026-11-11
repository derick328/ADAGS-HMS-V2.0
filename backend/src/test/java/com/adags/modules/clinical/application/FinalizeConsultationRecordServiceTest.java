package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.ConsultationRecordStatus;
import com.adags.modules.clinical.domain.DiagnosisCertainty;
import com.adags.modules.clinical.domain.InvalidConsultationRecordException;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 09-doctor-incomplete-consultations.md §5.1: "Required to finalize" —
// history_of_presenting_illness, physical_examination, provisional_diagnosis
// (a primary Diagnosis), treatment_plan. final_diagnosis stays nullable.
class FinalizeConsultationRecordServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private FinalizeConsultationRecordService finalizeService;

    @Autowired
    private AddDiagnosisService addDiagnosisService;

    @Autowired
    private ConsultationRecordRepository consultationRecordRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE diagnosis, consultation_record, encounter, patient, icd10_code CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void finalizeSucceedsWhenAllRequiredFieldsArePresent() {
        UUID encounterId = insertFixtureEncounter();
        UUID authorId = bootstrapAdminId();
        UUID consultationRecordId = openAndFillConsultationRecord(encounterId, authorId);
        addPrimaryDiagnosis(encounterId, consultationRecordId, authorId);

        ConsultationRecord finalized = finalizeService.finalize(encounterId, authorId, false, null);

        assertThat(finalized.status()).isEqualTo(ConsultationRecordStatus.FINALIZED);
        assertThat(finalized.completedAt()).isNotNull();
    }

    @Test
    void missingHistoryOfPresentingIllnessIsRejected() {
        UUID encounterId = insertFixtureEncounter();
        UUID authorId = bootstrapAdminId();
        UUID consultationRecordId = consultationRecordRepository.insert(ConsultationRecord.open(encounterId, authorId)).id();
        consultationRecordRepository.autosave(consultationRecordId, null, null, null, null, null,
                "Alert, mild pallor", null, "Awaiting malaria RDT");
        addPrimaryDiagnosis(encounterId, consultationRecordId, authorId);

        assertThatThrownBy(() -> finalizeService.finalize(encounterId, authorId, false, null))
                .isInstanceOf(InvalidConsultationRecordException.class)
                .hasMessageContaining("history_of_presenting_illness");
    }

    @Test
    void missingPrimaryDiagnosisIsRejected() {
        UUID encounterId = insertFixtureEncounter();
        UUID authorId = bootstrapAdminId();
        openAndFillConsultationRecord(encounterId, authorId);

        assertThatThrownBy(() -> finalizeService.finalize(encounterId, authorId, false, null))
                .isInstanceOf(InvalidConsultationRecordException.class)
                .hasMessageContaining("provisional_diagnosis");
    }

    // 07-doctor.md §4.4: labs pending is a legitimate reason to finalize
    // without a diagnosis being CONFIRMED — a primary diagnosis at any
    // certainty (including PROVISIONAL) satisfies the requirement.
    @Test
    void aProvisionalPrimaryDiagnosisSatisfiesTheRequirement() {
        UUID encounterId = insertFixtureEncounter();
        UUID authorId = bootstrapAdminId();
        UUID consultationRecordId = openAndFillConsultationRecord(encounterId, authorId);
        insertIcd10Code("B50.9", "P. falciparum malaria, unspecified");
        addDiagnosisService.addDiagnosis(encounterId, consultationRecordId, "B50.9", null,
                DiagnosisCertainty.PROVISIONAL, true, authorId);

        ConsultationRecord finalized = finalizeService.finalize(encounterId, authorId, false, null);

        assertThat(finalized.status()).isEqualTo(ConsultationRecordStatus.FINALIZED);
    }

    // 09-doc §5.2: a short retrospective note fails even though every
    // other required field is present.
    @Test
    void aRetrospectiveCompletionWithATooShortNoteIsRejected() {
        UUID encounterId = insertFixtureEncounter();
        UUID authorId = bootstrapAdminId();
        UUID consultationRecordId = openAndFillConsultationRecord(encounterId, authorId);
        addPrimaryDiagnosis(encounterId, consultationRecordId, authorId);

        assertThatThrownBy(() -> finalizeService.finalize(encounterId, authorId, true, "too short"))
                .isInstanceOf(InvalidConsultationRecordException.class)
                .hasMessageContaining("retrospective_note");
    }

    @Test
    void aRetrospectiveCompletionWithAnAdequateNoteSucceeds() {
        UUID encounterId = insertFixtureEncounter();
        UUID authorId = bootstrapAdminId();
        UUID consultationRecordId = openAndFillConsultationRecord(encounterId, authorId);
        addPrimaryDiagnosis(encounterId, consultationRecordId, authorId);

        ConsultationRecord finalized = finalizeService.finalize(encounterId, authorId, true,
                "Completed four days late, based on my written notes and the nurse's chart.");

        assertThat(finalized.retrospectiveCompletion()).isTrue();
    }

    @Test
    void finalizingWithNoOpenRecordIsRejected() {
        UUID encounterId = insertFixtureEncounter();

        assertThatThrownBy(() -> finalizeService.finalize(encounterId, bootstrapAdminId(), false, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    private UUID openAndFillConsultationRecord(UUID encounterId, UUID authorId) {
        UUID consultationRecordId = consultationRecordRepository.insert(ConsultationRecord.open(encounterId, authorId)).id();
        consultationRecordRepository.autosave(consultationRecordId, "Fever for 3 days", null, null, null, null,
                "Alert, mild pallor", null, "Awaiting malaria RDT");
        return consultationRecordId;
    }

    private void addPrimaryDiagnosis(UUID encounterId, UUID consultationRecordId, UUID authorId) {
        insertIcd10Code("B50.9", "P. falciparum malaria, unspecified");
        addDiagnosisService.addDiagnosis(encounterId, consultationRecordId, "B50.9", null,
                DiagnosisCertainty.PROVISIONAL, true, authorId);
    }

    private void insertIcd10Code(String code, String title) {
        jdbcTemplate.update("""
                        INSERT INTO icd10_code (code, title, icd10_release) VALUES (:code, :title, '2019')
                        ON CONFLICT (code) DO NOTHING
                        """,
                new MapSqlParameterSource().addValue("code", code).addValue("title", title));
    }

    private UUID insertFixtureEncounter() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

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
