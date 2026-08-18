package com.adags.modules.clinical;

import com.adags.modules.clinical.domain.AllergyConfirmationStatus;
import com.adags.modules.clinical.domain.AllergyRecord;
import com.adags.modules.clinical.domain.AllergyRecordRepository;
import com.adags.modules.clinical.domain.AllergySource;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 06-triage.md §2.4: "Allergies are written to the patient... so they
// persist to future visits" — append-only, never an update (root CLAUDE.md
// "Data").
class AllergyRecordRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private AllergyRecordRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE allergy_record, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void insertPersistsAndFindReturnsIt() {
        UUID patientId = insertFixturePatient();

        AllergyRecord saved = repository.insert(
                AllergyRecord.record(patientId, null, "Penicillin - rash", null, null, AllergySource.TRIAGE, bootstrapAdminId(), null));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.recordedAt()).isNotNull();

        List<AllergyRecord> found = repository.findByPatientId(patientId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).description()).isEqualTo("Penicillin - rash");
        assertThat(found.get(0).source()).isEqualTo(AllergySource.TRIAGE);
        assertThat(found.get(0).confirmationStatus()).isEqualTo(AllergyConfirmationStatus.UNCONFIRMED);
    }

    // A second triage recording the same or a different allergy text must
    // never overwrite the first — both are independently readable history.
    @Test
    void aSecondRecordingAppendsRatherThanReplacingTheFirst() {
        UUID patientId = insertFixturePatient();

        repository.insert(AllergyRecord.record(patientId, null, "Penicillin - rash", null, null, AllergySource.TRIAGE, bootstrapAdminId(), null));
        repository.insert(AllergyRecord.record(patientId, null, "Sulfa drugs - anaphylaxis", null, null, AllergySource.TRIAGE, bootstrapAdminId(), null));

        List<AllergyRecord> found = repository.findByPatientId(patientId);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(AllergyRecord::description)
                .containsExactlyInAnyOrder("Penicillin - rash", "Sulfa drugs - anaphylaxis");
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'FEMALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
