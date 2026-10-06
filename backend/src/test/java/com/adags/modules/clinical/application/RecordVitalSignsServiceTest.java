package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.Avpu;
import com.adags.modules.clinical.domain.ImplausibleVitalException;
import com.adags.modules.clinical.domain.VitalSigns;
import com.adags.modules.clinical.domain.VitalSignsContext;
import com.adags.modules.clinical.domain.VitalType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Wires item 1 (VitalReferenceRangeEvaluator) to real patient demographics
// and a real VitalReferenceRange lookup — 06-triage.md §2.2: "each vital is
// checked against the patient's age band via the evaluator from item 1.
// Implausible rejects with the bound named. Abnormal saves with a flag, and
// the applied range id is snapshotted."
class RecordVitalSignsServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RecordVitalSignsService service;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE vital_reading, vital_signs, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void normalAdultVitalsSaveWithNoAbnormalFlags() {
        UUID patientId = insertFixturePatient(LocalDate.of(1990, 1, 1), "MALE");
        UUID encounterId = insertFixtureEncounter(patientId);

        VitalSigns saved = service.record(adultCommand(encounterId, patientId));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.readings()).hasSize(6);
        assertThat(saved.readings()).allSatisfy(reading -> assertThat(reading.abnormal()).isFalse());
        assertThat(saved.readings()).allSatisfy(reading -> assertThat(reading.appliedRangeId()).isNotNull());
    }

    @Test
    void anAbnormalButPlausibleVitalSavesWithTheFlagSet() {
        UUID patientId = insertFixturePatient(LocalDate.of(1990, 1, 1), "MALE");
        UUID encounterId = insertFixtureEncounter(patientId);

        RecordVitalSignsCommand command = new RecordVitalSignsCommand(
                encounterId, patientId, VitalSignsContext.TRIAGE, UUID.randomUUID(),
                38.5, // above the adult abnormal_high of 38.0, still below implausible_high of 43
                120, 80, 80, 16, 98,
                70, 175, Avpu.ALERT, 5.0, null, null, null, bootstrapAdminId(), Instant.now());

        VitalSigns saved = service.record(command);

        boolean temperatureAbnormal = saved.readings().stream()
                .filter(r -> r.vitalType() == VitalType.TEMPERATURE)
                .findFirst().orElseThrow().abnormal();
        assertThat(temperatureAbnormal).isTrue();
    }

    @Test
    void anImplausibleVitalIsRejectedNamingTheBoundAndNothingIsSaved() {
        UUID patientId = insertFixturePatient(LocalDate.of(1990, 1, 1), "MALE");
        UUID encounterId = insertFixtureEncounter(patientId);

        RecordVitalSignsCommand command = new RecordVitalSignsCommand(
                encounterId, patientId, VitalSignsContext.TRIAGE, UUID.randomUUID(),
                44.0, // above implausible_high of 43
                120, 80, 80, 16, 98,
                70, 175, null, null, null, null, null, bootstrapAdminId(), Instant.now());

        assertThatThrownBy(() -> service.record(command))
                .isInstanceOf(ImplausibleVitalException.class)
                .hasMessageContaining("43");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vital_signs WHERE encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // V78 seeded paediatric bounds — a school-age child's vitals now record
    // (and get flagged abnormal per-vital) rather than throwing NotConfigured.
    // Values chosen to be within-range for SCHOOL_AGE (6-12y): HR 90, RR 20,
    // SpO2 99, BP 100/65, temp 37.0 — all inside V78's abnormal bounds.
    @Test
    void aSchoolAgeChildIsEvaluatedAgainstPaediatricBoundsRatherThanRefused() {
        UUID patientId = insertFixturePatient(LocalDate.now().minusYears(8), "FEMALE");
        UUID encounterId = insertFixtureEncounter(patientId);

        RecordVitalSignsCommand command = new RecordVitalSignsCommand(
                encounterId, patientId, VitalSignsContext.TRIAGE, UUID.randomUUID(),
                37.0, 100, 65, 90, 20, 99,
                25, 125, null, null, null, null, null, bootstrapAdminId(), Instant.now());

        service.record(command);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vital_signs WHERE encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(count).isEqualTo(1);
    }

    private RecordVitalSignsCommand adultCommand(UUID encounterId, UUID patientId) {
        return new RecordVitalSignsCommand(
                encounterId, patientId, VitalSignsContext.TRIAGE, UUID.randomUUID(),
                36.8, 120, 80, 75, 16, 98,
                70, 175, Avpu.ALERT, 5.0, null, null, null, bootstrapAdminId(), Instant.now());
    }

    private UUID insertFixturePatient(LocalDate dateOfBirth, String gender) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', :dateOfBirth, :gender, '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("hospitalNo", "TEST-" + UUID.randomUUID())
                        .addValue("dateOfBirth", dateOfBirth)
                        .addValue("gender", gender),
                UUID.class);
    }

    private UUID insertFixtureEncounter(UUID patientId) {
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
