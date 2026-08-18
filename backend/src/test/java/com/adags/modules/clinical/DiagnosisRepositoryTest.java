package com.adags.modules.clinical;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.Diagnosis;
import com.adags.modules.clinical.domain.DiagnosisCertainty;
import com.adags.modules.clinical.domain.DiagnosisRepository;
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

// 07-doctor.md §4.1.
class DiagnosisRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private DiagnosisRepository repository;

    @Autowired
    private ConsultationRecordRepository consultationRecordRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE diagnosis, consultation_record, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void insertPersistsTheTitleSnapshot() {
        UUID encounterId = insertFixtureEncounter();
        UUID consultationRecordId = openConsultationRecord(encounterId);

        Diagnosis saved = repository.insert(new Diagnosis(null, encounterId, consultationRecordId,
                "B50.9", "P. falciparum malaria, unspecified", "with severe anaemia",
                DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId(), null, null, null));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.icd10TitleSnapshot()).isEqualTo("P. falciparum malaria, unspecified");
        assertThat(saved.isPrimary()).isTrue();
    }

    @Test
    void existsPrimaryForEncounterReflectsAPrimaryDiagnosis() {
        UUID encounterId = insertFixtureEncounter();
        UUID consultationRecordId = openConsultationRecord(encounterId);
        assertThat(repository.existsPrimaryForEncounter(encounterId)).isFalse();

        repository.insert(new Diagnosis(null, encounterId, consultationRecordId,
                "B50.9", "P. falciparum malaria, unspecified", null,
                DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId(), null, null, null));

        assertThat(repository.existsPrimaryForEncounter(encounterId)).isTrue();
    }

    // §4.1: "Exactly one may be primary" — UNIQUE (encounter_id) WHERE is_primary.
    @Test
    void aSecondPrimaryDiagnosisForTheSameEncounterIsRejected() {
        UUID encounterId = insertFixtureEncounter();
        UUID consultationRecordId = openConsultationRecord(encounterId);
        repository.insert(new Diagnosis(null, encounterId, consultationRecordId,
                "B50.9", "P. falciparum malaria, unspecified", null,
                DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId(), null, null, null));

        assertThatThrownBy(() -> repository.insert(new Diagnosis(null, encounterId, consultationRecordId,
                "J18.9", "Pneumonia, unspecified organism", null,
                DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId(), null, null, null)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void multipleNonPrimaryDiagnosesAreAllowed() {
        UUID encounterId = insertFixtureEncounter();
        UUID consultationRecordId = openConsultationRecord(encounterId);
        repository.insert(new Diagnosis(null, encounterId, consultationRecordId,
                "B50.9", "P. falciparum malaria, unspecified", null,
                DiagnosisCertainty.PROVISIONAL, true, bootstrapAdminId(), null, null, null));

        Diagnosis secondary = repository.insert(new Diagnosis(null, encounterId, consultationRecordId,
                "E44.0", "Moderate protein-energy malnutrition", null,
                DiagnosisCertainty.PROVISIONAL, false, bootstrapAdminId(), null, null, null));

        assertThat(secondary.id()).isNotNull();
        assertThat(repository.findByEncounterId(encounterId)).hasSize(2);
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
