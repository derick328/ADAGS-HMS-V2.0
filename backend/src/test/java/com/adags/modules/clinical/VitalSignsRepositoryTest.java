package com.adags.modules.clinical;

import com.adags.modules.clinical.domain.Avpu;
import com.adags.modules.clinical.domain.RecordedVital;
import com.adags.modules.clinical.domain.VitalSigns;
import com.adags.modules.clinical.domain.VitalSignsContext;
import com.adags.modules.clinical.domain.VitalSignsRepository;
import com.adags.modules.clinical.domain.VitalType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

// V10__vital_signs_and_allergy_record.sql: the shared VitalSigns table
// (17-ward-nurse.md §4.1 — one table across TRIAGE/CONSULTATION/WARD_ROUNDS/
// POST_OP) plus its normalised vital_reading child rows.
class VitalSignsRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private VitalSignsRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE vital_reading, vital_signs, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void insertPersistsReadingsAndComputesBmi() {
        UUID encounterId = insertFixtureEncounter();
        UUID rangeId = currentAdultRangeId(VitalType.TEMPERATURE);

        VitalSigns toInsert = new VitalSigns(
                null, encounterId, VitalSignsContext.TRIAGE, UUID.randomUUID(),
                List.of(new RecordedVital(VitalType.TEMPERATURE, 36.8, false, rangeId)),
                70.0, 175.0, null, Avpu.ALERT, 5.5, null, null, null,
                bootstrapAdminId(), null, Instant.now());

        VitalSigns saved = repository.insert(toInsert);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.recordedAt()).isNotNull();
        assertThat(saved.bmi()).isCloseTo(70.0 / Math.pow(175.0 / 100.0, 2), offset(0.01));
        assertThat(saved.avpu()).isEqualTo(Avpu.ALERT);
        assertThat(saved.bloodGlucoseMmol()).isEqualTo(5.5);

        VitalSigns reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.readings()).hasSize(1);
        assertThat(reloaded.reading(VitalType.TEMPERATURE).value()).isEqualTo(36.8);
        assertThat(reloaded.reading(VitalType.TEMPERATURE).appliedRangeId()).isEqualTo(rangeId);
        assertThat(reloaded.reading(VitalType.TEMPERATURE).abnormal()).isFalse();
    }

    @Test
    void avpuAndBloodGlucoseAreNullableAndRoundTripAsNull() {
        UUID encounterId = insertFixtureEncounter();

        VitalSigns toInsert = new VitalSigns(
                null, encounterId, VitalSignsContext.TRIAGE, UUID.randomUUID(),
                List.of(), 60.0, 160.0, null, null, null, null, null, null,
                bootstrapAdminId(), null, Instant.now());

        VitalSigns saved = repository.insert(toInsert);
        VitalSigns reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.avpu()).isNull();
        assertThat(reloaded.bloodGlucoseMmol()).isNull();
    }

    @Test
    void findByEncounterIdReturnsNewestFirst() {
        UUID encounterId = insertFixtureEncounter();
        UUID first = insertVitalSigns(encounterId, Instant.now().minusSeconds(60)).id();
        UUID second = insertVitalSigns(encounterId, Instant.now()).id();

        List<VitalSigns> results = repository.findByEncounterId(encounterId);

        assertThat(results).extracting(VitalSigns::id).containsExactly(second, first);
    }

    // vital_reading's UNIQUE (vital_signs_id, vital_type) — the DB-level
    // guard behind "one applied range per vital per reading", independent
    // of RecordVitalSignsService only ever building one RecordedVital per type.
    @Test
    void vitalReadingRejectsASecondRowForTheSameVitalTypeOnTheSameVitalSigns() {
        UUID encounterId = insertFixtureEncounter();
        UUID vitalSignsId = insertVitalSigns(encounterId, Instant.now()).id();
        UUID rangeId = currentAdultRangeId(VitalType.PULSE);

        jdbcTemplate.update("""
                        INSERT INTO vital_reading (vital_signs_id, vital_type, value, applied_range_id, abnormal)
                        VALUES (:vitalSignsId, 'PULSE', 80, :rangeId, false)
                        """,
                new MapSqlParameterSource().addValue("vitalSignsId", vitalSignsId).addValue("rangeId", rangeId));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO vital_reading (vital_signs_id, vital_type, value, applied_range_id, abnormal)
                        VALUES (:vitalSignsId, 'PULSE', 82, :rangeId, false)
                        """,
                new MapSqlParameterSource().addValue("vitalSignsId", vitalSignsId).addValue("rangeId", rangeId)))
                .isInstanceOf(DataAccessException.class);
    }

    private VitalSigns insertVitalSigns(UUID encounterId, Instant occurredAt) {
        return repository.insert(new VitalSigns(
                null, encounterId, VitalSignsContext.TRIAGE, UUID.randomUUID(),
                List.of(), 65.0, 170.0, null, null, null, null, null, null,
                bootstrapAdminId(), null, occurredAt));
    }

    private UUID currentAdultRangeId(VitalType vitalType) {
        return jdbcTemplate.queryForObject("""
                        SELECT id FROM vital_reference_range
                        WHERE vital_type = :vitalType AND age_band = 'ADULT' AND sex = 'ANY' AND effective_to IS NULL
                        """,
                new MapSqlParameterSource("vitalType", vitalType.name()), UUID.class);
    }

    private UUID insertFixtureEncounter() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'FEMALE', '+255700000000')
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
