package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.AllergyConfirmationStatus;
import com.adags.modules.clinical.domain.AllergyRecord;
import com.adags.modules.clinical.domain.AllergyRecordRepository;
import com.adags.modules.clinical.domain.AllergySource;
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

// 07-doctor.md §6.1: "The doctor confirms or refutes what triage recorded."
class ReconcileAllergyRecordServiceTest extends IntegrationTestSupport {

    @Autowired
    private ReconcileAllergyRecordService service;

    @Autowired
    private AllergyRecordRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE allergy_record, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void confirmingSetsConfirmedStatusByAndAt() {
        UUID patientId = insertFixturePatient();
        UUID doctorId = bootstrapAdminId();
        AllergyRecord triageRecord = repository.insert(AllergyRecord.record(
                patientId, null, "penicillin - rash", null, null, AllergySource.TRIAGE, insertFixtureUser(), null));

        service.reconcile(triageRecord.id(), AllergyConfirmationStatus.CONFIRMED, doctorId, "Confirmed with patient's mother.");

        AllergyRecord reloaded = repository.findById(triageRecord.id()).orElseThrow();
        assertThat(reloaded.confirmationStatus()).isEqualTo(AllergyConfirmationStatus.CONFIRMED);
        assertThat(reloaded.confirmedBy()).isEqualTo(doctorId);
        assertThat(reloaded.confirmedAt()).isNotNull();
        // The clinical content is untouched by reconciliation.
        assertThat(reloaded.description()).isEqualTo("penicillin - rash");
    }

    @Test
    void refutingRequiresANoteAtTheServiceLevel() {
        UUID patientId = insertFixturePatient();
        AllergyRecord triageRecord = repository.insert(AllergyRecord.record(
                patientId, null, "penicillin - rash", null, null, AllergySource.TRIAGE, insertFixtureUser(), null));

        assertThatThrownBy(() -> service.reconcile(triageRecord.id(), AllergyConfirmationStatus.REFUTED, bootstrapAdminId(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // root CLAUDE.md "Data" / the CHECK constraint (V10 migration) as the
    // real guard, independent of the service's own pre-check.
    @Test
    void theDatabaseRejectsARefutalWithNoNoteEvenBypassingTheService() {
        UUID patientId = insertFixturePatient();
        AllergyRecord triageRecord = repository.insert(AllergyRecord.record(
                patientId, null, "penicillin - rash", null, null, AllergySource.TRIAGE, insertFixtureUser(), null));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE allergy_record SET confirmation_status = 'REFUTED', confirmed_by = :user, confirmed_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("user", bootstrapAdminId()).addValue("id", triageRecord.id())))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refutingWithANoteSucceedsAndBothStatusesDisplayIdentically() {
        // 07-doctor.md §6.1: UNCONFIRMED and REFUTED both display and warn — proven here
        // by confirming the refuted record still carries every field a display would need.
        UUID patientId = insertFixturePatient();
        AllergyRecord triageRecord = repository.insert(AllergyRecord.record(
                patientId, null, "penicillin - rash", null, null, AllergySource.TRIAGE, insertFixtureUser(), null));

        service.reconcile(triageRecord.id(), AllergyConfirmationStatus.REFUTED, bootstrapAdminId(),
                "Patient tolerated a penicillin course last year without reaction.");

        AllergyRecord reloaded = repository.findById(triageRecord.id()).orElseThrow();
        assertThat(reloaded.confirmationStatus()).isEqualTo(AllergyConfirmationStatus.REFUTED);
        assertThat(reloaded.description()).isEqualTo("penicillin - rash");
        assertThat(reloaded.confirmationNote()).isNotBlank();
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }

    private UUID insertFixtureUser() {
        String username = "fixture_" + UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", username), UUID.class);
    }
}
