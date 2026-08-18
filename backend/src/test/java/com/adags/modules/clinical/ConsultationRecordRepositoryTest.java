package com.adags.modules.clinical;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.ConsultationRecordStatus;
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

// 07-doctor.md §10, 09-doctor-incomplete-consultations.md §2.
class ConsultationRecordRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ConsultationRecordRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE consultation_record, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void insertOpensARecordAndAutosaveUpdatesItInPlace() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID authorId = bootstrapAdminId();

        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, authorId));
        assertThat(saved.status()).isEqualTo(ConsultationRecordStatus.OPEN);

        repository.autosave(saved.id(), "Fever for 3 days", null, null, null,
                "Alert, mild pallor", null, "Awaiting malaria RDT");

        ConsultationRecord reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.historyOfPresentingIllness()).isEqualTo("Fever for 3 days");
        assertThat(reloaded.physicalExamination()).isEqualTo("Alert, mild pallor");
        assertThat(reloaded.treatmentPlan()).isEqualTo("Awaiting malaria RDT");
        assertThat(reloaded.status()).isEqualTo(ConsultationRecordStatus.OPEN);
    }

    @Test
    void findOpenByEncounterAndAuthorReturnsOnlyTheOpenRow() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID authorId = bootstrapAdminId();
        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, authorId));

        assertThat(repository.findOpenByEncounterAndAuthor(encounterId, authorId)).isPresent();

        repository.finalize(saved.id(), Instant.now(), false, null);

        assertThat(repository.findOpenByEncounterAndAuthor(encounterId, authorId)).isEmpty();
    }

    @Test
    void finalizeSetsStatusAndCompletedAt() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, bootstrapAdminId()));

        repository.finalize(saved.id(), Instant.now(), true, "Completed from the incomplete list, based on nursing notes.");

        ConsultationRecord reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ConsultationRecordStatus.FINALIZED);
        assertThat(reloaded.completedAt()).isNotNull();
        assertThat(reloaded.retrospectiveCompletion()).isTrue();
    }

    // Autosave key (encounter_id, author_id): at most one OPEN row per pair.
    @Test
    void aSecondOpenRecordForTheSameEncounterAndAuthorIsRejected() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID authorId = bootstrapAdminId();
        repository.insert(ConsultationRecord.open(encounterId, authorId));

        assertThatThrownBy(() -> repository.insert(ConsultationRecord.open(encounterId, authorId)))
                .isInstanceOf(DataAccessException.class);
    }

    // A different author may still open their own record on the same
    // encounter — 07-doctor.md §7.1's internal-referral shape.
    @Test
    void aDifferentAuthorCanOpenTheirOwnRecordOnTheSameEncounter() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID firstAuthor = bootstrapAdminId();
        UUID secondAuthor = insertFixtureUser();
        repository.insert(ConsultationRecord.open(encounterId, firstAuthor));

        ConsultationRecord second = repository.insert(ConsultationRecord.open(encounterId, secondAuthor));

        assertThat(second.id()).isNotNull();
    }

    @Test
    void findIncompleteReturnsAnOldOpenRecordOnANonTerminalEncounter() {
        UUID authorId = bootstrapAdminId();
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, authorId));
        backdateCreatedAt(saved.id(), Instant.now().minusSeconds(3600 * 20));

        List<ConsultationRecord> incomplete = repository.findIncomplete(authorId, Instant.now().minusSeconds(3600 * 12));

        assertThat(incomplete).extracting(ConsultationRecord::id).contains(saved.id());
    }

    @Test
    void findIncompleteExcludesRecordsNewerThanTheThreshold() {
        UUID authorId = bootstrapAdminId();
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, authorId));

        List<ConsultationRecord> incomplete = repository.findIncomplete(authorId, Instant.now().minusSeconds(3600 * 12));

        assertThat(incomplete).extracting(ConsultationRecord::id).doesNotContain(saved.id());
    }

    @Test
    void findIncompleteExcludesFinalizedRecords() {
        UUID authorId = bootstrapAdminId();
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, authorId));
        backdateCreatedAt(saved.id(), Instant.now().minusSeconds(3600 * 20));
        repository.finalize(saved.id(), Instant.now(), false, null);

        List<ConsultationRecord> incomplete = repository.findIncomplete(authorId, Instant.now().minusSeconds(3600 * 12));

        assertThat(incomplete).extracting(ConsultationRecord::id).doesNotContain(saved.id());
    }

    // 09-doc §2 CRITICAL: "A blood culture takes five days. If
    // AWAITING_RESULTS encounters appear as incomplete, the doctor gets a
    // list they cannot clear." An OPEN record on an AWAITING_RESULTS
    // encounter must never appear here, even though status=OPEN alone
    // would otherwise match.
    @Test
    void findIncompleteExcludesAnOpenRecordOnAnAwaitingResultsEncounter() {
        UUID authorId = bootstrapAdminId();
        UUID encounterId = insertFixtureEncounter("AWAITING_RESULTS");
        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, authorId));
        backdateCreatedAt(saved.id(), Instant.now().minusSeconds(3600 * 20));

        List<ConsultationRecord> incomplete = repository.findIncomplete(authorId, Instant.now().minusSeconds(3600 * 12));

        assertThat(incomplete).extracting(ConsultationRecord::id).doesNotContain(saved.id());
    }

    @Test
    void findIncompleteExcludesTerminalEncounters() {
        UUID authorId = bootstrapAdminId();
        UUID encounterId = insertFixtureEncounter("CLOSED");
        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, authorId));
        backdateCreatedAt(saved.id(), Instant.now().minusSeconds(3600 * 20));

        List<ConsultationRecord> incomplete = repository.findIncomplete(authorId, Instant.now().minusSeconds(3600 * 12));

        assertThat(incomplete).extracting(ConsultationRecord::id).doesNotContain(saved.id());
    }

    @Test
    void findIncompleteExcludesADifferentAuthorsRecord() {
        UUID authorId = bootstrapAdminId();
        UUID otherAuthorId = insertFixtureUser();
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        ConsultationRecord saved = repository.insert(ConsultationRecord.open(encounterId, otherAuthorId));
        backdateCreatedAt(saved.id(), Instant.now().minusSeconds(3600 * 20));

        List<ConsultationRecord> incomplete = repository.findIncomplete(authorId, Instant.now().minusSeconds(3600 * 12));

        assertThat(incomplete).extracting(ConsultationRecord::id).doesNotContain(saved.id());
    }

    private void backdateCreatedAt(UUID consultationRecordId, Instant createdAt) {
        jdbcTemplate.update("UPDATE consultation_record SET created_at = :createdAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("createdAt", java.sql.Timestamp.from(createdAt))
                        .addValue("id", consultationRecordId));
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

    private UUID insertFixtureUser() {
        String username = "fixture_" + UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", username), UUID.class);
    }
}
