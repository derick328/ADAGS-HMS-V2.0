package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.Diagnosis;
import com.adags.modules.clinical.domain.DiagnosisCertainty;
import com.adags.modules.clinical.domain.DiagnosisRepository;
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

// 07-doctor.md §4.1: resolves and snapshots the ICD-10 title via
// ReferenceDataFacade rather than trusting a client-supplied title.
class AddDiagnosisServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private AddDiagnosisService service;

    @Autowired
    private VoidDiagnosisService voidDiagnosisService;

    @Autowired
    private DiagnosisRepository diagnosisRepository;

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
    void addDiagnosisSnapshotsTheCurrentTitle() {
        insertIcd10Code("B50.9", "P. falciparum malaria, unspecified", true, true);
        UUID encounterId = insertFixtureEncounter();
        UUID consultationRecordId = openConsultationRecord(encounterId);

        Diagnosis saved = service.addDiagnosis(encounterId, consultationRecordId, "B50.9",
                "with severe anaemia", DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId());

        assertThat(saved.icd10TitleSnapshot()).isEqualTo("P. falciparum malaria, unspecified");
        assertThat(saved.freeTextClarifier()).isEqualTo("with severe anaemia");
    }

    @Test
    void mistakenPrimaryCanBeVoidedAndReplacedWithoutErasingTheOriginal() {
        insertIcd10Code("B50.9", "P. falciparum malaria, unspecified", true, true);
        UUID encounterId = insertFixtureEncounter();
        UUID recordId = openConsultationRecord(encounterId);
        UUID authorId = bootstrapAdminId();
        Diagnosis mistaken = service.addDiagnosis(encounterId, recordId, "B50.9",
                null, DiagnosisCertainty.PROVISIONAL, true, authorId);

        voidDiagnosisService.voidDiagnosis(recordId, mistaken.id(), authorId);

        assertThat(diagnosisRepository.findByEncounterId(encounterId)).isEmpty();
        assertThat(diagnosisRepository.existsPrimaryForEncounter(encounterId)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT voided_by FROM diagnosis WHERE id = :id",
                new MapSqlParameterSource("id", mistaken.id()), UUID.class)).isEqualTo(authorId);
        Diagnosis replacement = service.addDiagnosis(encounterId, recordId, "B50.9",
                null, DiagnosisCertainty.CONFIRMED, true, authorId);
        assertThat(replacement.id()).isNotNull();
        assertThatThrownBy(() -> voidDiagnosisService.voidDiagnosis(recordId, mistaken.id(), authorId))
                .isInstanceOf(NoSuchElementException.class);

        jdbcTemplate.update("""
                UPDATE consultation_record SET status = 'FINALIZED', completed_at = now()
                WHERE id = :recordId
                """, new MapSqlParameterSource("recordId", recordId));
        assertThatThrownBy(() -> voidDiagnosisService.voidDiagnosis(recordId, replacement.id(), authorId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void aRetiredCodeIsRejected() {
        insertIcd10Code("A99", "Zika virus disease, retired", true, false);
        UUID encounterId = insertFixtureEncounter();
        UUID consultationRecordId = openConsultationRecord(encounterId);

        assertThatThrownBy(() -> service.addDiagnosis(encounterId, consultationRecordId, "A99",
                null, DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void aNonBillableCategoryHeaderIsRejected() {
        insertIcd10Code("B50", "Plasmodium falciparum malaria", false, true);
        UUID encounterId = insertFixtureEncounter();
        UUID consultationRecordId = openConsultationRecord(encounterId);

        assertThatThrownBy(() -> service.addDiagnosis(encounterId, consultationRecordId, "B50",
                null, DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void anUnknownCodeIsRejected() {
        UUID encounterId = insertFixtureEncounter();
        UUID consultationRecordId = openConsultationRecord(encounterId);

        assertThatThrownBy(() -> service.addDiagnosis(encounterId, consultationRecordId, "Z99.9",
                null, DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    private void insertIcd10Code(String code, String title, boolean isBillable, boolean isActive) {
        jdbcTemplate.update("""
                        INSERT INTO icd10_code (code, title, icd10_release, is_billable, is_active, is_selectable)
                        VALUES (:code, :title, '2019', :isBillable, :isActive, :isBillable)
                        """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("title", title)
                        .addValue("isBillable", isBillable)
                        .addValue("isActive", isActive));
    }

    private UUID openConsultationRecord(UUID encounterId) {
        ConsultationRecord record = consultationRecordRepository.insert(
                ConsultationRecord.open(encounterId, bootstrapAdminId()));
        return record.id();
    }

    private UUID insertFixtureEncounter() {
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
