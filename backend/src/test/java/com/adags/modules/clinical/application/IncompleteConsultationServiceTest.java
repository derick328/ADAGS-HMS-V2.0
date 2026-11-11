package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
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

// 09-doctor-incomplete-consultations.md §2: "The 12-hour threshold is
// configuration (ProtocolSetting), not a constant." V14 seeds the default
// (12); this proves an admin-tuned value is actually honoured.
class IncompleteConsultationServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private IncompleteConsultationService service;

    @Autowired
    private ConsultationRecordRepository consultationRecordRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE consultation_record, encounter, patient CASCADE",
                new MapSqlParameterSource());
        jdbcTemplate.update("""
                        UPDATE protocol_setting SET setting_value = '12'::jsonb
                        WHERE setting_key = 'consultation.incomplete_prompt_hours'
                        """, new MapSqlParameterSource());
    }

    @Test
    void usesTheDefaultTwelveHourThreshold() {
        UUID authorId = bootstrapAdminId();
        UUID encounterId = insertFixtureEncounter();
        UUID recordId = openRecordBackdated(encounterId, authorId, 20);

        List<ConsultationRecord> incomplete = service.findIncomplete(authorId);

        assertThat(incomplete).extracting(ConsultationRecord::id).contains(recordId);
    }

    @Test
    void aRetunedThresholdIsHonoured() {
        jdbcTemplate.update("""
                        UPDATE protocol_setting SET setting_value = '2'::jsonb
                        WHERE setting_key = 'consultation.incomplete_prompt_hours'
                        """, new MapSqlParameterSource());
        UUID authorId = bootstrapAdminId();
        UUID encounterId = insertFixtureEncounter();
        // 5 hours old: below the default 12h threshold, above a retuned 2h one.
        UUID recordId = openRecordBackdated(encounterId, authorId, 5);

        List<ConsultationRecord> incomplete = service.findIncomplete(authorId);

        assertThat(incomplete).extracting(ConsultationRecord::id).contains(recordId);
    }

    private UUID openRecordBackdated(UUID encounterId, UUID authorId, int hoursOld) {
        UUID recordId = consultationRecordRepository.insert(ConsultationRecord.open(encounterId, authorId)).id();
        jdbcTemplate.update("UPDATE consultation_record SET created_at = :createdAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("createdAt", java.sql.Timestamp.from(Instant.now().minusSeconds(3600L * hoursOld)))
                        .addValue("id", recordId));
        return recordId;
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
